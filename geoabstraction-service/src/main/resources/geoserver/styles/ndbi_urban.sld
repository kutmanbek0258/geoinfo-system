<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>ndbi_urban_style</Name>
    <UserStyle>
      <Title>NDBI Built-up Infrastructure Index</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#FFFFFF" quantity="-1.00" opacity="0.0" label="Природные объекты"/>
              <ColorMapEntry color="#FFFFFF" quantity="0.00" opacity="0.0" label="Граница урбанизации"/>
              <ColorMapEntry color="#FEE08B" quantity="0.10" opacity="1.0" label="Пригород / Сельская застройка"/>
              <ColorMapEntry color="#FDAE61" quantity="0.25" opacity="1.0" label="Разреженная городская среда"/>
              <ColorMapEntry color="#F46D43" quantity="0.40" opacity="1.0" label="Плотная жилая застройка"/>
              <ColorMapEntry color="#D53E4F" quantity="0.60" opacity="1.0" label="Индустриальные зоны / Бетон"/>
              <ColorMapEntry color="#7E154A" quantity="1.00" opacity="1.0" label="Сверхплотный центр / Асфальт"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
