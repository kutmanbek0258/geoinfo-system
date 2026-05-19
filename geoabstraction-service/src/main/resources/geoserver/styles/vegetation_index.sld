<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc" 
    xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>vegetation_index_style</Name>
    <UserStyle>
      <Title>Vegetation Index (NDVI/SAVI/EVI) Ramp</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <Opacity>1.0</Opacity>
            <ColorMap type="ramp">
              <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/>
              <ColorMapEntry color="#0000FF" quantity="-1.00" opacity="1.0" label="Вода / Снег"/>
              <ColorMapEntry color="#E29B63" quantity="0.00" opacity="1.0" label="Голая почва / Застройка"/>
              <ColorMapEntry color="#FCEB92" quantity="0.20" opacity="1.0" label="Разреженная вегетация"/>
              <ColorMapEntry color="#A3DB6D" quantity="0.40" opacity="1.0" label="Умеренная растительность"/>
              <ColorMapEntry color="#328846" quantity="0.60" opacity="1.0" label="Здоровая растительность"/>
              <ColorMapEntry color="#0A4318" quantity="1.00" opacity="1.0" label="Плотный здоровый лес"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
