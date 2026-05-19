<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>ndsi_snow_style</Name>
    <UserStyle>
      <Title>NDSI Snow and Ice Mapping</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#FFFFFF" quantity="-1.00" opacity="0.0" label="Нет снега"/>
              <ColorMapEntry color="#FFFFFF" quantity="0.00" opacity="0.0" label="Граница заснежения"/>
              <ColorMapEntry color="#A5C9EB" quantity="0.20" opacity="1.0" label="Смешанные пиксели / Тающий снег"/>
              <ColorMapEntry color="#00FFFF" quantity="0.50" opacity="1.0" label="Плотный лед / Фирн"/>
              <ColorMapEntry color="#FFFFFF" quantity="1.00" opacity="1.0" label="Свежий глубокий снег"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
