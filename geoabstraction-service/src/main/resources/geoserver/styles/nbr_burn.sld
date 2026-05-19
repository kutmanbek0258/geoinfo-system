<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>nbr_burn_style</Name>
    <UserStyle>
      <Title>NBR Burn Severity Index</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#1A1A1A" quantity="-0.50" opacity="1.0" label="Очаг пожара / Зола"/>
              <ColorMapEntry color="#D73027" quantity="-0.25" opacity="1.0" label="Высокая степень выгорания"/>
              <ColorMapEntry color="#F46D43" quantity="-0.10" opacity="1.0" label="Умеренное повреждение огнем"/>
              <ColorMapEntry color="#FEE08B" quantity="0.00" opacity="1.0" label="Низкая степень / Восстановление"/>
              <ColorMapEntry color="#D9EF8B" quantity="0.10" opacity="1.0" label="Граница гари / Открытый грунт"/>
              <ColorMapEntry color="#1A9641" quantity="0.50" opacity="1.0" label="Здоровая нетронутая вегетация"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
