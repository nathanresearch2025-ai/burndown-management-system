"""
HTTP 客户端 — 封装后端 REST API 调用，自动管理 JWT Token
"""

import logging
import time
from typing import Any, Optional

import httpx

logger = logging.getLogger("mcp-scrum.api")


class ApiClient:
    def __init__(self, base_url: str, username: str, password: str):
        self._base_url = base_url.rstrip("/")
        self._username = username
        self._password = password
        self._token: Optional[str] = None
        self._token_expiry: float = 0.0
        self._client = httpx.AsyncClient(timeout=30.0)

    async def _get_token(self) -> str:
        """获取有效 JWT Token，自动刷新（6天缓存）"""
        now = time.time()
        if self._token and now < self._token_expiry:
            return self._token

        logger.info(f"正在登录后端，用户: {self._username}")
        resp = await self._client.post(
            f"{self._base_url}/auth/login",
            json={"username": self._username, "password": self._password},
        )
        resp.raise_for_status()
        data = resp.json()
        self._token = data["token"]
        self._token_expiry = now + 6 * 24 * 3600  # 6天后刷新
        logger.info("登录成功，Token 已缓存")
        return self._token

    async def _headers(self) -> dict:
        token = await self._get_token()
        return {"Authorization": f"Bearer {token}"}

    async def get(self, path: str, params: Optional[dict] = None) -> Any:
        headers = await self._headers()
        url = f"{self._base_url}{path}"
        logger.info(f"[API] GET {url}")
        if params:
            logger.debug(f"[API] params={params}")
        resp = await self._client.get(url, headers=headers, params=params)
        self._raise_for_status(resp)
        logger.info(f"[API] GET {url} -> {resp.status_code}")
        return resp.json()

    async def post(self, path: str, body: Optional[dict] = None) -> Any:
        headers = await self._headers()
        url = f"{self._base_url}{path}"
        logger.info(f"[API] POST {url}")
        if body:
            logger.debug(f"[API] body={body}")
        resp = await self._client.post(url, headers=headers, json=body)
        self._raise_for_status(resp)
        logger.info(f"[API] POST {url} -> {resp.status_code}")
        if resp.status_code == 204 or not resp.content:
            return None
        return resp.json()

    async def patch(self, path: str, params: Optional[dict] = None, body: Optional[dict] = None) -> Any:
        headers = await self._headers()
        url = f"{self._base_url}{path}"
        logger.info(f"PATCH {url}")
        resp = await self._client.patch(url, headers=headers, params=params, json=body)
        self._raise_for_status(resp)
        return resp.json()

    def _raise_for_status(self, resp: httpx.Response):
        if resp.status_code >= 400:
            try:
                detail = resp.json()
                msg = detail.get("message", resp.text)
            except Exception:
                msg = resp.text
            raise RuntimeError(f"后端错误 {resp.status_code}: {msg}")

    async def close(self):
        await self._client.aclose()
