<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>gndvi_style</Name>
    <UserStyle>
      <Title>GNDVI Chlorophyll Mapping</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#7A4D1B" quantity="0.00" opacity="1.0" label="Отсутствие растительности"/>
              <ColorMapEntry color="#E67E22" quantity="0.20" opacity="1.0" label="Сильный стресс / Дефицит азота"/>
              <ColorMapEntry color="#F1C40F" quantity="0.40" opacity="1.0" label="Удовлетворительное состояние"/>
              <ColorMapEntry color="#2ECC71" quantity="0.60" opacity="1.0" label="Хорошее насыщение хлорофиллом"/>
              <ColorMapEntry color="#27AE60" quantity="0.75" opacity="1.0" label="Высокая активность биомассы"/>
              <ColorMapEntry color="#145A32" quantity="1.00" opacity="1.0" label="Пик вегетации / Максимум азота"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
