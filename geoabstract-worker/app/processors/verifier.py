import os
import shutil
import tempfile
import zipfile
import tarfile
import glob
import re
from typing import Any, Dict, List, Optional
from osgeo import gdal
from .base import BaseProcessor
from ..core.config import logger
from ..core.clients import minio_client
from ..core.gdal import get_wgs84_extent

class VerifierProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        job_id = str(job_data.get("jobId"))
        output_prefix = job_data.get("outputPrefix")
        task_type = job_data.get("taskType", "VERIFY_FILE")
        characteristics = job_data.get("characteristics", {})
        data_type = characteristics.get("dataType", "").upper()

        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]
        
        work_dir = tempfile.mkdtemp(prefix="verify-{0}-".format(job_id))
        input_file = os.path.join(work_dir, "input_source")

        try:
            self.send_status(job_id, "PROCESSING", task_type, output_prefix=output_prefix)
            
            # Determine extension from key to keep original if possible
            _, ext = os.path.splitext(source_key)
            if ext:
                input_file += ext

            logger.info("Downloading file for verification: %s/%s -> %s", source_bucket, source_key, input_file)
            minio_client.fget_object(source_bucket, source_key, input_file)

            metadata = {}
            bbox = None

            if data_type in ("GEOTIFF", "TERRAIN"):
                # Check if it's a ZIP archive containing a TIF
                extracted_file = self._try_extract_raster(input_file, work_dir)
                ds = gdal.Open(extracted_file)
                if not ds:
                    raise RuntimeError("Failed to open file as GeoTIFF dataset using GDAL")
                
                # Extract metadata
                metadata = self._extract_raster_metadata(ds, extracted_file)
                bbox = get_wgs84_extent(extracted_file)
                ds = None

            elif data_type in ("SENTINEL_2", "LANDSAT_8"):
                # Must be an archive
                extract_dir = os.path.join(work_dir, "extracted")
                os.makedirs(extract_dir, exist_ok=True)
                self._extract_archive(input_file, extract_dir)

                # Scan bands
                if data_type == "SENTINEL_2":
                    band_patterns = ["*B01*", "*B02*", "*B03*", "*B04*", "*B05*", "*B06*", "*B07*", "*B08*", "*B8A*", "*B09*", "*B10*", "*B11*", "*B12*"]
                    found_bands = self._scan_archive_bands(extract_dir, band_patterns)
                    metadata["availableBands"] = list(found_bands.keys())
                    metadata["satelliteType"] = "sentinel2"
                    
                    # Try to get bbox from B04 or B03 or B02
                    ref_band = None
                    for b in ["B04", "B03", "B02", "B08"]:
                        if b in found_bands:
                            ref_band = found_bands[b]
                            break
                    if ref_band:
                        ds = gdal.Open(ref_band)
                        if ds:
                            metadata["crs"] = ds.GetProjection()
                            bbox = get_wgs84_extent(ref_band)
                            ds = None
                else:  # LANDSAT_8
                    band_patterns = ["*_B1.*", "*_B2.*", "*_B3.*", "*_B4.*", "*_B5.*", "*_B6.*", "*_B7.*", "*_B9.*", "*_B10.*", "*_B11.*"]
                    found_bands = self._scan_archive_bands(extract_dir, band_patterns)
                    metadata["availableBands"] = list(found_bands.keys())
                    metadata["satelliteType"] = "landsat8"
                    
                    ref_band = None
                    for b in ["B4", "B3", "B2", "B5"]:
                        if b in found_bands:
                            ref_band = found_bands[b]
                            break
                    if ref_band:
                        ds = gdal.Open(ref_band)
                        if ds:
                            metadata["crs"] = ds.GetProjection()
                            bbox = get_wgs84_extent(ref_band)
                            ds = None

            elif data_type == "3D_TILES":
                metadata = {"format": "OBJ_OR_ZIP", "fileSize": os.path.getsize(input_file)}
                if zipfile.is_zipfile(input_file):
                    with zipfile.ZipFile(input_file, "r") as z:
                        file_list = z.namelist()
                        obj_files = [f for f in file_list if f.lower().endswith(".obj")]
                        if not obj_files:
                            raise RuntimeError("ZIP archive does not contain any .obj files")
                        metadata["containedObjFile"] = obj_files[0]
                elif input_file.lower().endswith(".obj"):
                    metadata["containedObjFile"] = os.path.basename(input_file)
                else:
                    raise RuntimeError("Uploaded file must be a .obj file or a .zip archive containing a .obj model")

            elif data_type == "CITYGML":
                metadata = self._verify_citygml(input_file, work_dir)

            elif data_type == "SHAPEFILE":
                from osgeo import ogr
                vsi_path = f"/vsizip/{input_file}"
                ds = ogr.Open(vsi_path)
                if not ds:
                    raise RuntimeError("Uploaded ZIP archive is not a valid Shapefile package")
                total_features = 0
                layer_names = []
                geom_types = set()
                for i in range(ds.GetLayerCount()):
                    layer = ds.GetLayerByIndex(i)
                    total_features += layer.GetFeatureCount()
                    layer_names.append(layer.GetName())
                    geom_type_code = layer.GetGeomType()
                    geom_types.add(ogr.GeometryTypeToName(geom_type_code))
                
                metadata["totalFeatures"] = total_features
                metadata["layerNames"] = layer_names
                metadata["geomTypes"] = list(geom_types)
                metadata["format"] = "SHAPEFILE_ZIP"
                ds = None

            else:
                raise RuntimeError("Unsupported data type for verification: {0}".format(data_type))


            # Send back READY with characteristics containing metadata
            self.send_status(
                job_id, 
                "READY", 
                task_type, 
                output_prefix=output_prefix, 
                bbox=bbox, 
                characteristics={"metadata": metadata, "dataType": data_type}
            )

        except Exception as e:
            logger.exception("Verification failed for job %s", job_id)
            self.send_status(job_id, "FAILED", task_type, error_message=str(e), output_prefix=output_prefix)
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        pass

    def _try_extract_raster(self, input_file: str, work_dir: str) -> str:
        if zipfile.is_zipfile(input_file):
            logger.info("Decompressing ZIP containing GeoTIFF")
            extract_dir = os.path.join(work_dir, "extracted")
            os.makedirs(extract_dir, exist_ok=True)
            with zipfile.ZipFile(input_file, "r") as z:
                z.extractall(extract_dir)
            tifs = glob.glob(os.path.join(extract_dir, "**/*.tif"), recursive=True) + \
                   glob.glob(os.path.join(extract_dir, "**/*.tiff"), recursive=True)
            if not tifs:
                raise RuntimeError("No GeoTIFF file found in ZIP archive")
            return tifs[0]
        return input_file

    def _try_extract_netcdf(self, input_file: str, work_dir: str) -> str:
        if zipfile.is_zipfile(input_file):
            logger.info("Decompressing ZIP containing NetCDF")
            extract_dir = os.path.join(work_dir, "extracted")
            os.makedirs(extract_dir, exist_ok=True)
            with zipfile.ZipFile(input_file, "r") as z:
                z.extractall(extract_dir)
            nc_files = glob.glob(os.path.join(extract_dir, "**/*.nc"), recursive=True) + \
                       glob.glob(os.path.join(extract_dir, "**/*.nc4"), recursive=True)
            if not nc_files:
                raise RuntimeError("No NetCDF file found in ZIP archive")
            return nc_files[0]
        return input_file

    def _extract_raster_metadata(self, ds: gdal.Dataset, filepath: str) -> Dict[str, Any]:
        proj = ds.GetProjection()
        width = ds.RasterXSize
        height = ds.RasterYSize
        bands_count = ds.RasterCount
        
        bands_info = []
        for i in range(1, bands_count + 1):
            band = ds.GetRasterBand(i)
            bands_info.append({
                "index": i,
                "dataType": gdal.GetDataTypeName(band.DataType),
                "noDataValue": band.GetNoDataValue(),
            })

        return {
            "crs": proj,
            "width": width,
            "height": height,
            "bandsCount": bands_count,
            "bands": bands_info,
            "filename": os.path.basename(filepath),
        }

    def _extract_netcdf_metadata(self, ds: gdal.Dataset, filepath: str) -> Dict[str, Any]:
        subdatasets = ds.GetSubDatasets()
        
        parsed_subdatasets = []
        for ds_name, ds_desc in subdatasets:
            var_name = ds_name
            m = re.match(r'^NETCDF:".*":(.*)$', ds_name)
            if m:
                var_name = m.group(1)
            else:
                m2 = re.match(r'^NETCDF:(.*):(.*)$', ds_name)
                if m2:
                    var_name = m2.group(2)
            
            parsed_subdatasets.append({
                "subdatasetKey": ds_name,
                "variableName": var_name,
                "description": ds_desc
            })

        return {
            "crs": ds.GetProjection(),
            "width": ds.RasterXSize,
            "height": ds.RasterYSize,
            "subdatasets": parsed_subdatasets,
            "filename": os.path.basename(filepath),
        }

    def _extract_archive(self, file_path: str, extract_dir: str) -> None:
        if zipfile.is_zipfile(file_path):
            with zipfile.ZipFile(file_path, "r") as ref:
                ref.extractall(extract_dir)
        elif tarfile.is_tarfile(file_path):
            with tarfile.open(file_path, "r:*") as ref:
                ref.extractall(extract_dir)
        else:
            raise RuntimeError("Unsupported archive format for verification")

    def _scan_archive_bands(self, extract_dir: str, patterns: List[str]) -> Dict[str, str]:
        found_bands = {}
        for pattern in patterns:
            matches = glob.glob(os.path.join(extract_dir, "**/" + pattern), recursive=True)
            if matches:
                clean_name = pattern.replace("*", "").replace(".", "")
                if "_" in clean_name:
                    clean_name = clean_name.split("_")[1]
                found_bands[clean_name] = matches[0]
        return found_bands

    def _verify_citygml(self, input_file: str, work_dir: str) -> Dict[str, Any]:
        gml_file = input_file
        if zipfile.is_zipfile(input_file):
            extract_dir = os.path.join(work_dir, "gml_extracted")
            os.makedirs(extract_dir, exist_ok=True)
            with zipfile.ZipFile(input_file, "r") as z:
                z.extractall(extract_dir)
            gml_files = [
                os.path.join(root, f)
                for root, _, files in os.walk(extract_dir)
                for f in files if f.lower().endswith((".gml", ".xml"))
            ]
            if not gml_files:
                raise RuntimeError("ZIP archive does not contain any .gml or .xml CityGML files")
            gml_file = gml_files[0]
        elif not input_file.lower().endswith((".gml", ".xml")):
            raise RuntimeError("Uploaded file must be a .gml, .xml file or a .zip archive containing CityGML data")

        with open(gml_file, "r", encoding="utf-8", errors="ignore") as f:
            header = f.read(500000)

        srs_match = re.search(r'srsName=["\']([^"\']+)["\']', header, re.IGNORECASE)
        srs_name = srs_match.group(1) if srs_match else "Unknown SRS"
        
        building_matches = re.findall(r'<[^:]+:Building\b|<Building\b', header, re.IGNORECASE)

        return {
            "format": "CityGML",
            "gmlFile": os.path.basename(gml_file),
            "srsName": srs_name,
            "buildingCount": len(building_matches),
            "fileSize": os.path.getsize(input_file)
        }
