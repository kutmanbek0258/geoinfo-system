FROM ghcr.io/tum-gis/ctb-quantized-mesh:latest

# Fix Debian Buster EOL repositories
RUN sed -i 's/deb.debian.org/archive.debian.org/g' /etc/apt/sources.list && \
    sed -i 's/security.debian.org/archive.debian.org/g' /etc/apt/sources.list && \
    sed -i '/buster-updates/d' /etc/apt/sources.list

# Install Python tooling
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-dev \
    build-essential \
    libffi-dev \
    libssl-dev \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Upgrade pip first
RUN pip3 install --upgrade pip setuptools wheel

# Install versions compatible with Python 3.7
RUN pip3 install --no-cache-dir \
    kafka-python==2.0.2 \
    minio==7.1.15 \
    importlib-metadata==6.7.0

WORKDIR /app

COPY worker.py .

CMD ["python3", "worker.py"]
