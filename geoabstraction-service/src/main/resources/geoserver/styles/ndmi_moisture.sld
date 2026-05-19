<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>ndmi_moisture_style</Name>
    <UserStyle>
      <Title>NDMI Vegetation Moisture Stress</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#990000" quantity="-1.00" opacity="1.0" label="Сухая голая почва"/>
              <ColorMapEntry color="#D73027" quantity="-0.20" opacity="1.0" label="Экстремальный водный стресс"/>
              <ColorMapEntry color="#FDAE61" quantity="0.00" opacity="1.0" label="Высокий стресс / Увядание"/>
              <ColorMapEntry color="#EEF9BF" quantity="0.10" opacity="1.0" label="Умеренный / Начальный стресс"/>
              <ColorMapEntry color="#4575B4" quantity="0.40" opacity="1.0" label="Достаточное влагосодержание"/>
              <ColorMapEntry color="#053061" quantity="1.00" opacity="1.0" label="Полное насыщение / Избыток влаги"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
