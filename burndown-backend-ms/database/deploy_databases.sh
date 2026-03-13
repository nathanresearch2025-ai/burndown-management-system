#!/bin/bash
set -e

echo "=========================================="
echo "Deploying Databases to Kubernetes"
echo "=========================================="

# Deploy PostgreSQL for auth_db
echo "Deploying PostgreSQL for auth_db..."
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres-auth
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres-auth
  template:
    metadata:
      labels:
        app: postgres-auth
    spec:
      hostNetwork: true
      containers:
      - name: postgres
        image: postgres:16
        ports:
        - containerPort: 5433
        env:
        - name: POSTGRES_DB
          value: auth_db
        - name: POSTGRES_USER
          value: postgres
        - name: POSTGRES_PASSWORD
          value: root
        - name: PGPORT
          value: "5433"
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
          subPath: data
      volumes:
      - name: postgres-storage
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-auth
spec:
  selector:
    app: postgres-auth
  ports:
  - port: 5433
    targetPort: 5433
    nodePort: 30433
  type: NodePort
EOF

# Deploy PostgreSQL for core_db
echo "Deploying PostgreSQL for core_db..."
kubectl apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres-core
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres-core
  template:
    metadata:
      labels:
        app: postgres-core
    spec:
      hostNetwork: true
      containers:
      - name: postgres
        image: postgres:16
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: core_db
        - name: POSTGRES_USER
          value: postgres
        - name: POSTGRES_PASSWORD
          value: root
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
          subPath: data
      volumes:
      - name: postgres-storage
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-core
spec:
  selector:
    app: postgres-core
  ports:
  - port: 5432
    targetPort: 5432
    nodePort: 30432
  type: NodePort
EOF

echo "Waiting for PostgreSQL pods to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres-auth --timeout=120s || true
kubectl wait --for=condition=ready pod -l app=postgres-core --timeout=120s || true
sleep 10

# Initialize auth_db
echo "Initializing auth_db..."
AUTH_POD=$(kubectl get pod -l app=postgres-auth -o jsonpath='{.items[0].metadata.name}')
kubectl cp auth_db_init.sql ${AUTH_POD}:/tmp/init.sql
kubectl exec -i ${AUTH_POD} -- psql -U postgres -d auth_db -f /tmp/init.sql

# Initialize core_db
echo "Initializing core_db..."
CORE_POD=$(kubectl get pod -l app=postgres-core -o jsonpath='{.items[0].metadata.name}')
kubectl cp core_db_init.sql ${CORE_POD}:/tmp/init.sql
kubectl exec -i ${CORE_POD} -- psql -U postgres -d core_db -f /tmp/init.sql

echo "=========================================="
echo "Database deployment completed!"
echo "=========================================="
echo "Auth DB: localhost:30433"
echo "Core DB: localhost:30432"
