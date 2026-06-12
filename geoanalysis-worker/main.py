from app.core.orchestrator import Orchestrator
from app.core.config import logger

def main():
    logger.info("GeoAnalysis worker starting...")
    try:
        orchestrator = Orchestrator()
        orchestrator.start()
    except Exception as e:
        logger.exception(f"Critical error in main: {e}")

if __name__ == "__main__":
    main()
