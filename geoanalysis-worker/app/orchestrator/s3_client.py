import os
from minio import Minio
from urllib.parse import urlparse
from ..core.config import (
    MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_SECURE,
    logger
)

class S3Client:
    def __init__(self):
        self.client = Minio(
            MINIO_ENDPOINT.replace("http://", "").replace("https://", ""),
            access_key=MINIO_ACCESS_KEY,
            secret_key=MINIO_SECRET_KEY,
            secure=MINIO_SECURE,
        )

    def parse_s3_url(self, url: str):
        """Парсит s3://bucket/path/to/file"""
        parsed = urlparse(url)
        if parsed.scheme != 's3':
            raise ValueError(f"Invalid S3 URL scheme: {parsed.scheme}")
        return parsed.netloc, parsed.path.lstrip('/')

    def download_file(self, s3_url: str, local_path: str):
        bucket, key = self.parse_s3_url(s3_url)
        logger.info(f"Downloading {s3_url} to {local_path}")
        self.client.fget_object(bucket, key, local_path)
        return local_path

    def upload_file(self, local_path: str, bucket: str, destination_key: str) -> str:
        if not self.client.bucket_exists(bucket):
            self.client.make_bucket(bucket)
        
        logger.info(f"Uploading {local_path} to s3://{bucket}/{destination_key}")
        self.client.fput_object(bucket, destination_key, local_path)
        return f"s3://{bucket}/{destination_key}"
