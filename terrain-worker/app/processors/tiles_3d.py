import os
import re
import json
import shutil
import tempfile
import zipfile
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import numpy as np
import trimesh
import mapbox_earcut
from PIL import Image
from lxml import etree
from pyproj import Transformer
from py3dtiles import B3dm, GlTF
from py3dtiles.utils import convert_to_ecef
from .base import BaseProcessor
from ..core.config import logger, TILES_3D_BASE_DIR
from ..core.clients import minio_client
from ..core.gdal import run_command

def clean_finite(val: float, default: float = 0.0) -> float:
    return float(val) if np.isfinite(val) else default

class Tiles3DProcessor(BaseProcessor):
    def process(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        job_id = str(job_data.get("jobId"))
        job_name = job_data.get("name", "")
        task_type = job_data.get("taskType", "3D_TILES")
        characteristics = job_data.get("characteristics", {})
        
        if not job_id or not output_prefix:
            raise RuntimeError("jobId or outputPrefix is missing")

        final_output_path = os.path.join(TILES_3D_BASE_DIR, output_prefix)
        source_bucket = job_data["sourceBucket"]
        source_key = job_data["sourceObjectKey"]

        work_dir = tempfile.mkdtemp(prefix=f"tiles3d-{job_id}-")
        
        orig_ext = os.path.splitext(source_key)[1]
        if not orig_ext:
            orig_ext = ".gml" if task_type == "CITYGML" else ".obj"
            
        downloaded_file = os.path.join(work_dir, f"input_source{orig_ext}")
        temp_tiles_dir = os.path.join(work_dir, "tileset_output")

        try:
            self.send_status(job_id, "PROCESSING", task_type, output_prefix=output_prefix)
            
            logger.info("Downloading raw 3D model file: %s/%s -> %s", source_bucket, source_key, downloaded_file)
            minio_client.fget_object(source_bucket, source_key, downloaded_file)

            target_file_path = None
            
            # 1. Check if ZIP archive
            if downloaded_file.lower().endswith(".zip") or zipfile.is_zipfile(downloaded_file):
                logger.info("Extracting ZIP archive: %s", downloaded_file)
                extracted_dir = os.path.join(work_dir, "extracted")
                os.makedirs(extracted_dir, exist_ok=True)
                with zipfile.ZipFile(downloaded_file, 'r') as zip_ref:
                    zip_ref.extractall(extracted_dir)
                
                target_exts = (".gml", ".xml") if task_type == "CITYGML" else (".obj", ".gml", ".xml")
                for root, _, files in os.walk(extracted_dir):
                    for f in files:
                        if f.lower().endswith(target_exts):
                            target_file_path = os.path.join(root, f)
                            break
                    if target_file_path:
                        break
                
                if not target_file_path:
                    raise RuntimeError(f"No suitable 3D file found in the extracted ZIP archive for task {task_type}")
            else:
                target_file_path = downloaded_file

            logger.info("Processing 3D model file (%s): %s", task_type, target_file_path)
            os.makedirs(temp_tiles_dir, exist_ok=True)

            ext = os.path.splitext(target_file_path)[1].lower()
            
            if task_type == "CITYGML" or ext in [".gml", ".xml"]:
                logger.info("Converting CityGML to 3D Tiles (b3dm + tileset.json)...")
                self._convert_citygml_to_3dtiles(target_file_path, temp_tiles_dir, job_name, characteristics)
            elif ext in [".las", ".laz", ".xyz"]:
                logger.info("Running py3dtiles convert CLI for point cloud...")
                run_command(["py3dtiles", "convert", "--out", temp_tiles_dir, target_file_path])
            else:
                logger.info("Converting OBJ model to 3D Tiles (b3dm + tileset.json)...")
                self._convert_obj_to_3dtiles(target_file_path, temp_tiles_dir, job_name, characteristics)

            tileset_json_path = os.path.join(temp_tiles_dir, "tileset.json")
            if not os.path.exists(tileset_json_path):
                raise RuntimeError("tileset.json was not generated")

            logger.info("Saving generated 3D Tiles to store: %s", final_output_path)
            self.save_tree(temp_tiles_dir, final_output_path)

            tileset_url = f"/3dtiles/{output_prefix}/tileset.json"
            self.send_status(
                job_id,
                "READY",
                task_type,
                output_prefix=output_prefix,
                terrainUrl=tileset_url
            )
            logger.info("3D Tiles job %s (%s) completed successfully: %s", job_id, task_type, tileset_url)

        except Exception as e:
            logger.exception("3D Tiles job %s failed", job_id)
            self.send_status(
                job_id,
                "FAILED",
                task_type,
                error_message=str(e),
                output_prefix=output_prefix
            )
        finally:
            if os.path.exists(work_dir):
                shutil.rmtree(work_dir, ignore_errors=True)

    def _convert_citygml_to_3dtiles(
        self,
        gml_path: str,
        output_dir: str,
        job_name: str,
        characteristics: Dict[str, Any]
    ) -> None:
        include_textures = bool(characteristics.get("includeTextures", True))
        logger.info("CityGML conversion includeTextures option: %s", include_textures)
        
        polygons_info, srs_name = self._parse_citygml_polygons_and_textures(gml_path, include_textures=include_textures)
        if not polygons_info:
            raise RuntimeError("No valid 3D polygon geometry found in CityGML file")
            
        logger.info("Extracted %d 3D polygons from CityGML (SRS: %s)", len(polygons_info), srs_name)

        transformer = self._create_srs_transformer(srs_name, characteristics)
        
        wgs84_polygons_info = []
        all_lons, all_lats, all_heights = [], [], []

        for item in polygons_info:
            wgs84_poly = []
            for (x, y, z) in item["pts"]:
                if not (np.isfinite(x) and np.isfinite(y) and np.isfinite(z)):
                    continue
                if transformer:
                    try:
                        lon, lat, h = transformer.transform(x, y, z)
                    except Exception:
                        continue
                else:
                    lon, lat, h = x, y, z
                
                if np.isfinite(lon) and np.isfinite(lat) and np.isfinite(h) and -180 <= lon <= 180 and -90 <= lat <= 90:
                    wgs84_poly.append((lon, lat, h))
                    all_lons.append(lon)
                    all_lats.append(lat)
                    all_heights.append(h)
                
            if len(wgs84_poly) >= 3:
                wgs84_polygons_info.append({
                    "pts": wgs84_poly,
                    "image_path": item.get("image_path"),
                    "uvs": item.get("uvs")
                })

        if not all_lons or not all_lats or not all_heights:
            raise RuntimeError("No valid geographic WGS84 coordinates could be computed from CityGML geometry")

        center_lon = float(np.mean(all_lons))
        center_lat = float(np.mean(all_lats))
        base_h = float(np.min(all_heights))
        
        logger.info("CityGML WGS84 Center: Lon: %f, Lat: %f, Base Height: %f", center_lon, center_lat, base_h)

        enu_polygons_info = []
        for item in wgs84_polygons_info:
            enu_poly = []
            for (lon, lat, h) in item["pts"]:
                dx = (lon - center_lon) * (111319.5 * np.cos(np.radians(center_lat)))
                dy = (lat - center_lat) * 111319.5
                dz = h - base_h
                enu_poly.append([clean_finite(dx), clean_finite(dy), clean_finite(dz)])
                
            enu_polygons_info.append({
                "pts": np.array(enu_poly),
                "image_path": item.get("image_path"),
                "uvs": item.get("uvs")
            })

        mesh = self._triangulate_3d_polygons_fast(enu_polygons_info, include_textures=include_textures)
        
        glb_data = mesh.export(file_type='glb')
        gltf = GlTF.from_array(np.frombuffer(glb_data, dtype=np.uint8))
        b3dm = B3dm.from_glTF(gltf)
        
        b3dm_filename = "model.b3dm"
        b3dm_path = os.path.join(output_dir, b3dm_filename)
        b3dm.save_as(Path(b3dm_path))

        bounds = mesh.bounds
        min_pt, max_pt = bounds[0], bounds[1]
        center = (min_pt + max_pt) / 2.0
        half_size = (max_pt - min_pt) / 2.0
        
        box = [
            clean_finite(center[0]), clean_finite(center[1]), clean_finite(center[2]),
            clean_finite(half_size[0]), 0.0, 0.0,
            0.0, clean_finite(half_size[1]), 0.0,
            0.0, 0.0, clean_finite(half_size[2])
        ]

        transform_matrix = [clean_finite(v) for v in self._get_ecef_transform_matrix(center_lon, center_lat, base_h)]

        tileset = {
            "asset": {
                "version": "1.0",
                "gltfUpAxis": "Z"
            },
            "geometricError": clean_finite(float(np.max(max_pt - min_pt)), 100.0),
            "root": {
                "transform": transform_matrix,
                "boundingVolume": {
                    "box": box
                },
                "geometricError": 0.0,
                "refine": "ADD",
                "content": {
                    "uri": b3dm_filename
                }
            }
        }

        tileset_path = os.path.join(output_dir, "tileset.json")
        with open(tileset_path, "w", encoding="utf-8") as f:
            json.dump(tileset, f, indent=2)

    def _parse_citygml_polygons_and_textures(
        self, 
        gml_path: str,
        include_textures: bool = True
    ) -> Tuple[List[Dict[str, Any]], str]:
        gml_dir = os.path.dirname(os.path.abspath(gml_path))
        tree = etree.parse(gml_path)
        root = tree.getroot()

        srs_name = ""
        for elem in root.iter():
            if "srsName" in elem.attrib:
                srs_name = elem.attrib["srsName"]
                break

        texture_mappings: Dict[str, Dict[str, Any]] = {}
        
        if include_textures:
            for elem in root.iter():
                tag = elem.tag.split("}")[-1] if "}" in elem.tag else elem.tag
                if tag == "ParameterizedTexture":
                    image_rel_path = ""
                    for child in elem:
                        ctag = child.tag.split("}")[-1] if "}" in child.tag else child.tag
                        if ctag == "imageURI":
                            image_rel_path = (child.text or "").strip()
                            break
                    
                    if not image_rel_path: continue
                    full_img_path = os.path.join(gml_dir, image_rel_path.replace("\\", "/"))
                    
                    for child in elem:
                        ctag = child.tag.split("}")[-1] if "}" in child.tag else child.tag
                        if ctag == "target":
                            uri = child.attrib.get("uri", "")
                            poly_id = uri.lstrip("#")
                            if not poly_id: continue

                            for subchild in child.iter():
                                stag = subchild.tag.split("}")[-1] if "}" in subchild.tag else subchild.tag
                                if stag == "textureCoordinates":
                                    text = (subchild.text or "").strip()
                                    if text:
                                        try:
                                            vals = [float(v) for v in text.split()]
                                            uvs = [(vals[i], vals[i+1]) for i in range(0, len(vals)-1, 2)]
                                            texture_mappings[poly_id] = {
                                                "image_path": full_img_path,
                                                "uvs": uvs
                                            }
                                        except Exception:
                                            pass

        polygons = []
        for elem in root.iter():
            tag = elem.tag.split("}")[-1] if "}" in elem.tag else elem.tag
            if tag in ("Polygon", "Triangle"):
                poly_id = elem.attrib.get("{http://www.opengis.net/gml}id") or elem.attrib.get("id") or ""
                
                pts = []
                for subchild in elem.iter():
                    stag = subchild.tag.split("}")[-1] if "}" in subchild.tag else subchild.tag
                    if stag == "posList":
                        text = (subchild.text or "").strip()
                        if text:
                            try:
                                vals = [float(v) for v in text.split()]
                                if len(vals) >= 9 and len(vals) % 3 == 0:
                                    pts = [(vals[i], vals[i+1], vals[i+2]) for i in range(0, len(vals), 3)]
                                    break
                            except Exception:
                                pass
                    elif stag == "coordinates":
                        text = (subchild.text or "").strip()
                        if text:
                            try:
                                for token in text.split():
                                    coords = [float(c) for c in token.split(",")]
                                    if len(coords) == 3:
                                        pts.append((coords[0], coords[1], coords[2]))
                                if len(pts) >= 3:
                                    break
                            except Exception:
                                pass
                
                if pts:
                    tex_info = texture_mappings.get(poly_id, {})
                    polygons.append({
                        "id": poly_id,
                        "pts": pts,
                        "image_path": tex_info.get("image_path"),
                        "uvs": tex_info.get("uvs")
                    })

        return polygons, srs_name

    def _create_srs_transformer(self, srs_name: str, characteristics: Dict[str, Any]) -> Optional[Transformer]:
        crs_str = characteristics.get("crs") or srs_name
        if not crs_str:
            return None
            
        epsg_match = re.search(r'EPSG[:_]?(\d+)', crs_str, re.IGNORECASE)
        if epsg_match:
            epsg_code = f"EPSG:{epsg_match.group(1)}"
            if epsg_code == "EPSG:4326":
                return None
            try:
                logger.info("Creating PyProj Transformer for %s -> EPSG:4326", epsg_code)
                return Transformer.from_crs(epsg_code, "EPSG:4326", always_xy=True)
            except Exception as e:
                logger.warning("Failed to create transformer for %s: %s", epsg_code, e)
                
        return None

    def _build_texture_atlas(
        self, 
        enu_polygons_info: List[Dict[str, Any]]
    ) -> Tuple[Optional[Image.Image], Dict[str, Tuple[float, float, float, float]]]:
        unique_paths = list(set([
            item["image_path"] for item in enu_polygons_info 
            if item.get("image_path") and os.path.exists(item["image_path"])
        ]))

        if not unique_paths:
            return None, {}

        logger.info("Building Texture Atlas for %d unique texture images...", len(unique_paths))
        
        total_cells = len(unique_paths) + 1
        cols = int(np.ceil(np.sqrt(total_cells)))
        rows = int(np.ceil(total_cells / cols))

        cell_w = max(64, min(256, 4096 // cols))
        cell_h = max(64, min(256, 4096 // rows))

        atlas_w = cols * cell_w
        atlas_h = rows * cell_h

        atlas_img = Image.new("RGB", (atlas_w, atlas_h), color=(220, 220, 225))
        uv_map: Dict[str, Tuple[float, float, float, float]] = {}

        for idx, path in enumerate(unique_paths, start=1):
            r = idx // cols
            c = idx % cols
            x_px = c * cell_w
            y_px = r * cell_h

            try:
                with Image.open(path) as img:
                    resized = img.convert("RGB").resize((cell_w, cell_h))
                    atlas_img.paste(resized, (x_px, y_px))
                    
                u_off = x_px / float(atlas_w)
                v_off = (atlas_h - (y_px + cell_h)) / float(atlas_h)
                u_scale = cell_w / float(atlas_w)
                v_scale = cell_h / float(atlas_h)
                uv_map[path] = (u_off, v_off, u_scale, v_scale)
            except Exception as e:
                logger.warning("Failed to paste texture image %s: %s", path, e)

        return atlas_img, uv_map

    def _triangulate_3d_polygons_fast(
        self, 
        enu_polygons_info: List[Dict[str, Any]],
        include_textures: bool = True
    ) -> trimesh.Trimesh:
        vertices_list = []
        uvs_list = []
        faces_list = []
        vertex_offset = 0

        atlas_img = None
        uv_transform_map = {}

        if include_textures:
            atlas_img, uv_transform_map = self._build_texture_atlas(enu_polygons_info)

        for item in enu_polygons_info:
            poly = item["pts"]
            uvs = item.get("uvs")
            img_path = item.get("image_path")

            if len(poly) < 3: continue
            if np.allclose(poly[0], poly[-1]):
                poly = poly[:-1]
                if uvs and len(uvs) > len(poly):
                    uvs = uvs[:-1]

            if len(poly) < 3: continue

            v0, v1, v2 = poly[0], poly[1], poly[2]
            normal = np.cross(v1 - v0, v2 - v0)
            norm_len = np.linalg.norm(normal)
            if norm_len == 0: continue
            normal = normal / norm_len

            tangent = np.array([1.0, 0.0, 0.0])
            if abs(np.dot(tangent, normal)) > 0.9:
                tangent = np.array([0.0, 1.0, 0.0])
            bitangent = np.cross(normal, tangent)
            bitangent = bitangent / np.linalg.norm(bitangent)
            tangent = np.cross(bitangent, normal)

            poly_2d = np.column_stack([
                np.dot(poly - v0, tangent),
                np.dot(poly - v0, bitangent)
            ])

            rings = np.array([len(poly_2d)], dtype=np.uint32)
            triangles = mapbox_earcut.triangulate_float32(poly_2d.astype(np.float32), rings)
            if len(triangles) == 0: continue

            vertices_list.append(poly)

            # Process Atlas UVs
            if atlas_img and img_path in uv_transform_map and uvs and len(uvs) == len(poly):
                u_off, v_off, u_scale, v_scale = uv_transform_map[img_path]
                poly_uvs = []
                for (u, v) in uvs:
                    u_atl = u_off + (u % 1.0) * u_scale
                    v_atl = v_off + (v % 1.0) * v_scale
                    poly_uvs.append([u_atl, v_atl])
                uvs_list.append(np.array(poly_uvs))
            else:
                uvs_list.append(np.zeros((len(poly), 2)))

            faces_list.append(triangles.reshape(-1, 3) + vertex_offset)
            vertex_offset += len(poly)

        if not vertices_list:
            return trimesh.creation.box()

        all_verts = np.vstack(vertices_list)
        all_uvs = np.vstack(uvs_list)
        all_faces = np.vstack(faces_list)

        unified_mesh = trimesh.Trimesh(vertices=all_verts, faces=all_faces, process=False)
        if atlas_img:
            unified_mesh.visual = trimesh.visual.TextureVisuals(uv=all_uvs, image=atlas_img)
        else:
            unified_mesh.visual.face_colors = [220, 220, 225, 255]

        return unified_mesh

    def _convert_obj_to_3dtiles(
        self, 
        obj_path: str, 
        output_dir: str, 
        job_name: str, 
        characteristics: Dict[str, Any]
    ) -> None:
        logger.info("Loading mesh using trimesh: %s", obj_path)
        scene_or_mesh = trimesh.load(obj_path, force='mesh')
        
        glb_data = scene_or_mesh.export(file_type='glb')
        gltf = GlTF.from_array(np.frombuffer(glb_data, dtype=np.uint8))
        b3dm = B3dm.from_glTF(gltf)
        
        b3dm_filename = "model.b3dm"
        b3dm_path = os.path.join(output_dir, b3dm_filename)
        b3dm.save_as(Path(b3dm_path))
        
        bounds = scene_or_mesh.bounds
        min_pt, max_pt = bounds[0], bounds[1]
        center = (min_pt + max_pt) / 2.0
        half_size = (max_pt - min_pt) / 2.0
        
        box = [
            clean_finite(center[0]), clean_finite(center[1]), clean_finite(center[2]),
            clean_finite(half_size[0]), 0.0, 0.0,
            0.0, clean_finite(half_size[1]), 0.0,
            0.0, 0.0, clean_finite(half_size[2])
        ]
        
        geometric_error = clean_finite(float(np.max(max_pt - min_pt)), 100.0)

        root_dict: Dict[str, Any] = {
            "boundingVolume": {
                "box": box
            },
            "geometricError": 0.0,
            "refine": "ADD",
            "content": {
                "uri": b3dm_filename
            }
        }

        lon_lat = self._determine_location(job_name, characteristics)
        if lon_lat:
            lon, lat = lon_lat
            height = float(characteristics.get("height", 0.0))
            logger.info("Georeferencing 3D Tileset to Lon: %f, Lat: %f, Height: %f", lon, lat, height)
            transform_matrix = [clean_finite(v) for v in self._get_ecef_transform_matrix(lon, lat, height)]
            root_dict["transform"] = transform_matrix
            
        tileset = {
            "asset": {
                "version": "1.0",
                "gltfUpAxis": "Z"
            },
            "geometricError": geometric_error,
            "root": root_dict
        }
        
        tileset_path = os.path.join(output_dir, "tileset.json")
        with open(tileset_path, "w", encoding="utf-8") as f:
            json.dump(tileset, f, indent=2)

    def _determine_location(self, job_name: str, characteristics: Dict[str, Any]) -> Optional[Tuple[float, float]]:
        if "longitude" in characteristics and "latitude" in characteristics:
            try:
                return float(characteristics["longitude"]), float(characteristics["latitude"])
            except Exception:
                pass

        osgb_coords = self._parse_osgb_grid_tile(job_name)
        if osgb_coords:
            return osgb_coords
            
        return None

    def _parse_osgb_grid_tile(self, name: str) -> Optional[Tuple[float, float]]:
        if not name: return None
        match = re.search(r'([A-Z]{2})\s*(\d{2})\s*(\d{2})(?:[_\s]*(NW|NE|SW|SE))?', name.upper())
        if not match: return None
        square, e_km, n_km, quad = match.groups()
        grid_offsets = {
            'TQ': (5, 1), 'SU': (3, 1), 'TV': (5, 0), 'TR': (5, 1), 'TL': (4, 2), 'TM': (5, 2),
            'SZ': (4, 0), 'SY': (3, 0), 'SX': (2, 0), 'SW': (1, 0)
        }
        if square not in grid_offsets: return None
        sq_e, sq_n = grid_offsets[square]
        easting = sq_e * 100000 + int(e_km) * 1000
        northing = sq_n * 100000 + int(n_km) * 1000
        if quad == 'NE': easting += 500; northing += 500
        elif quad == 'NW': northing += 500
        elif quad == 'SE': easting += 500
        try:
            transformer = Transformer.from_crs("EPSG:27700", "EPSG:4326", always_xy=True)
            lon, lat = transformer.transform(easting, northing)
            return lon, lat
        except Exception:
            return None

    def _get_ecef_transform_matrix(self, lon_deg: float, lat_deg: float, height_m: float = 0.0) -> list:
        lon = np.radians(lon_deg)
        lat = np.radians(lat_deg)
        x0, y0, z0 = convert_to_ecef(lat_deg, lon_deg, height_m, 4326)
        e = np.array([-np.sin(lon), np.cos(lon), 0.0])
        n = np.array([-np.sin(lat) * np.cos(lon), -np.sin(lat) * np.sin(lon), np.cos(lat)])
        u = np.array([np.cos(lat) * np.cos(lon), np.cos(lat) * np.sin(lon), np.sin(lat)])
        return [
            float(e[0]), float(e[1]), float(e[2]), 0.0,
            float(n[0]), float(n[1]), float(n[2]), 0.0,
            float(u[0]), float(u[1]), float(u[2]), 0.0,
            float(x0),   float(y0),   float(z0),   1.0
        ]

    def cleanup(self, job_data: Dict[str, Any]) -> None:
        output_prefix = job_data.get("outputPrefix")
        if not output_prefix: return

        target = os.path.join(TILES_3D_BASE_DIR, output_prefix)
        logger.info("Deleting 3D Tiles data at %s", target)
        try:
            if os.path.isdir(target):
                shutil.rmtree(target)
            elif os.path.isfile(target):
                os.remove(target)
        except Exception:
            logger.exception("Failed to delete 3D Tiles data at %s", target)

    def save_tree(self, src_dir: str, dst_dir: str) -> None:
        if os.path.exists(dst_dir):
            shutil.rmtree(dst_dir)
        os.makedirs(os.path.dirname(dst_dir), exist_ok=True)
        shutil.copytree(src_dir, dst_dir)
