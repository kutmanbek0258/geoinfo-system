<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>ndwi_water_style</Name>
    <UserStyle>
      <Title>NDWI Open Water Mask</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#FFFFFF" quantity="-1.00" opacity="0.0" label="Суша (Игнорировать)"/>
              <ColorMapEntry color="#FFFFFF" quantity="0.00" opacity="0.0" label="Граница суши"/>
              <ColorMapEntry color="#7AC5CD" quantity="0.10" opacity="1.0" label="Переувлажненные зоны / Болота"/>
              <ColorMapEntry color="#4A90E2" quantity="0.30" opacity="1.0" label="Мелководье / Мелкие водоемы"/>
              <ColorMapEntry color="#0A2240" quantity="1.00" opacity="1.0" label="Глубокая открытая вода"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
