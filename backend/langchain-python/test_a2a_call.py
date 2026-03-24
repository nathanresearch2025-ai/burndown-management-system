import requests
import json

url = "http://localhost:8091/agent/standup/a2a"
params = {"force_a2a": "true"}
payload = {
    "question": "测试 A2A 调用链",
    "projectId": 1,
    "sprintId": 1,
    "userId": 1,
    "traceId": "a2a-test-001"
}

print("发送请求...")
print(f"URL: {url}")
print(f"Params: {params}")
print(f"Payload: {json.dumps(payload, ensure_ascii=False, indent=2)}")

try:
    response = requests.post(url, params=params, json=payload, timeout=60)
    print(f"\n状态码: {response.status_code}")
    print(f"响应: {json.dumps(response.json(), ensure_ascii=False, indent=2)}")
except Exception as e:
    print(f"\n错误: {e}")
    if hasattr(e, 'response'):
        print(f"响应文本: {e.response.text}")
