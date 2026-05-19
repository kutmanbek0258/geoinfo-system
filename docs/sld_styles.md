Для каждого спектрального индекса, упомянутого в спецификациях Landsat 8 и Sentinel-2, ниже представлены готовые конфигурации SLD (Styled Layer Descriptor) версии 1.0.0. Эти стили предназначены для импорта в GeoServer для растровых слоев (RasterSymbolizer).

Каждая цветовая карта (ColorMap) настроена в режиме type="ramp" (плавный градиент). Если требуется жесткое разделение на зоны (как на тематических картах), замените этот параметр на type="intervals".

### 1. Единый стиль для индексов биомассы (NDVI, SAVI, EVI)

Применяется для отображения плотности и здоровья растительного покрова. Использует классический переход от открытого грунта к насыщенному широколиственному лесу.

```xml
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
```

### 2. Стиль для агроаналитики и хлорофилла (GNDVI)

Оптимизирован под повышенную чувствительность индекса к содержанию хлорофилла и азота в увядающих или созревающих посевах. Смещен в сторону более детального отображения градаций зеленого цвета.

```xml
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
```

### 3. Стиль влажности и водного стресса растений (NDMI)

Используется для мониторинга засухи и деградации почв. Цветовая палитра наглядно переходит от критических зон засухи (красный) к зонам нормального насыщения влагой (синий).

```xml
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
```

### 4. Стиль для анализа гарей и пожаров (NBR)

Предназначен для оценки степени повреждения растительности огнем. Обугленные участки и зола выделяются темными тонами, выгоревшие поля — алым, а нетронутый лес остается зеленым.

```xml
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
```

### 5. Стиль для выделения открытых водных объектов (NDWI)

Служит для батиметрии, оконтуривания береговой линии и речных сетей. Значения ниже нуля (суша) маскируются полной прозрачностью (opacity="0.0"), чтобы слой не перекрывал базовую подложку.

```xml
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
```

### 6. Стиль для детекции снега и льда (NDSI)

Разделяет облачность и снежный покров в горных и приполярных регионах. Суша скрыта прозрачностью, снег подсвечивается характерным ярко-циановым и белым цветом.

```xml
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
```

### 7. Стиль городской застройки и инфраструктуры (NDBI)

Используется в урбанистике для автоматического картирования дорожных сетей, зданий, промзон и искусственных покрытий. Плотная застройка выделяется ярко-пурпурным или красным цветом.

```xml
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
```

### Рекомендации по интеграции в ваш рабочий процесс:

- Автоматизация через REST API: Вы можете сохранить эти конфигурации как .sld файлы и загружать их в GeoServer программно, отправляя POST-запросы на /rest/styles.

- Тип NoData: Обратите внимание, что если ваш worker.py записывает значение NoData как -9999, GeoServer автоматически обработает его как прозрачный пиксель, если в свойствах слоя включена опция "Use Background Values". Тем не менее, рекомендуется в начале каждого стиля явно добавлять строку <ColorMapEntry color="#000000" quantity="-9999" opacity="0.0" label="NoData"/> для защиты от появления черных полей по краям снимка.
