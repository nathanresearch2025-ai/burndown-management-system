FROM postgres:15

# Install pgvector from pre-built package
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        postgresql-15-pgvector && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
