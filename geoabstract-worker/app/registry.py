from typing import Dict, List, Tuple

# -----------------------------------------------------------------------------
# Sentinel-2 Registry
# -----------------------------------------------------------------------------

SENTINEL2_INDEX_FORMULAS = {
    "NDVI": "(B8 - B4) / (B8 + B4)",
    "EVI": "2.5 * (B8 - B4) / (B8 + 6 * B4 - 7.5 * B2 + 1)",
    "SAVI": "1.5 * (B8 - B4) / (B8 + B4 + 0.5)",
    "GNDVI": "(B8 - B3) / (B8 + B3)",
    "NDMI": "(B8 - B11) / (B8 + B11)",
    "NBR": "(B8 - B12) / (B8 + B12)",
    "NDSI": "(B3 - B11) / (B3 + B11)",
    "NDBI": "(B11 - B8) / (B11 + B8)",
    "NDWI": "(B3 - B8) / (B3 + B8)",
}

SENTINEL2_INDEX_BANDS = {
    "NDVI": ["B08", "B04"],
    "EVI": ["B08", "B04", "B02"],
    "SAVI": ["B08", "B04"],
    "GNDVI": ["B08", "B03"],
    "NDMI": ["B08", "B11"],
    "NBR": ["B08", "B12"],
    "NDSI": ["B03", "B11"],
    "NDBI": ["B11", "B08"],
    "NDWI": ["B03", "B08"],
}

# -----------------------------------------------------------------------------
# Landsat 8 Registry
# -----------------------------------------------------------------------------

LANDSAT8_INDEX_FORMULAS = {
    "NDVI": "(B5 - B4) / (B5 + B4)",
    "EVI": "2.5 * (B5 - B4) / (B5 + 6 * B4 - 7.5 * B2 + 1)",
    "SAVI": "((B5 - B4) / (B5 + B4 + 0.5)) * 1.5",
    "MSAVI": "(2 * B5 + 1 - sqrt((2 * B5 + 1)**2 - 8 * (B5 - B4))) / 2",
    "NDMI": "(B5 - B6) / (B5 + B6)",
    "NBR": "(B5 - B7) / (B5 + B7)",
    "NBR2": "(B6 - B7) / (B6 + B7)",
    "NDSI": "(B3 - B6) / (B3 + B6)",
    "NDWI": "(B3 - B5) / (B3 + B5)",
}

LANDSAT8_INDEX_BANDS = {
    "NDVI": ["B5", "B4"],
    "EVI": ["B5", "B4", "B2"],
    "SAVI": ["B5", "B4"],
    "MSAVI": ["B5", "B4"],
    "NDMI": ["B5", "B6"],
    "NBR": ["B5", "B7"],
    "NBR2": ["B6", "B7"],
    "NDSI": ["B3", "B6"],
    "NDWI": ["B3", "B5"],
}

def get_formula_and_bands(satellite_type: str, index_type: str) -> Tuple[str, List[str]]:
    if satellite_type == "sentinel2":
        return SENTINEL2_INDEX_FORMULAS.get(index_type), SENTINEL2_INDEX_BANDS.get(index_type)
    elif satellite_type == "landsat8":
        return LANDSAT8_INDEX_FORMULAS.get(index_type), LANDSAT8_INDEX_BANDS.get(index_type)
    return None, None

def build_gdal_calc_formula(formula: str, expected_bands: List[str]) -> str:
    """
    Converts band names in formula to GDAL calc variables (A, B, C...)
    """
    replacements: List[Tuple[str, str]] = []
    for idx, band in enumerate(expected_bands):
        var = chr(ord("A") + idx)
        # Handle cases like B08 vs B8
        canonical = band.upper()
        replacements.append((canonical, var))
        if canonical.startswith("B") and "0" in canonical:
             replacements.append((canonical.replace("0", ""), var))

    # Sort replacements by length descending to avoid partial matches
    for token, var in sorted(replacements, key=lambda kv: len(kv[0]), reverse=True):
        formula = formula.replace(token, var)

    return formula
