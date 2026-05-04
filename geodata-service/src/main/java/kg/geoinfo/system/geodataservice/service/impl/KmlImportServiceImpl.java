package kg.geoinfo.system.geodataservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.geoinfo.system.common.GeoObjectEvent;
import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.mapper.ProjectMapper;
import kg.geoinfo.system.geodataservice.models.*;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.service.KmlImportService;
import kg.geoinfo.system.geodataservice.service.client.DocumentServiceClient;
import kg.geoinfo.system.geodataservice.service.kafka.KafkaProducerService;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.kml.KMLReader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KmlImportServiceImpl implements KmlImportService {

    private final ProjectRepository projectRepository;
    private final ProjectPointRepository projectPointRepository;
    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectPolygonRepository projectPolygonRepository;
    private final KafkaProducerService kafkaProducerService;
    private final DocumentServiceClient documentServiceClient;
    private final ObjectMapper objectMapper;
    private final ProjectMapper projectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    @Transactional
    public ProjectDto importKml(String currentUserEmail, MultipartFile file, String projectName) {
        log.info("Starting import for user: {}, file: {}, project: {}", currentUserEmail, file.getOriginalFilename(), projectName);

        try {
            KmzContent kmzContent = getKmzContent(file);
            Document doc = parseKmlFromStream(kmzContent.getKmlStream());
            Map<String, Map<String, Object>> styles = parseStyles(doc, kmzContent.getResources());
            
            String kmlProjectName = null;
            NodeList docNodes = doc.getElementsByTagNameNS("*", "Document");
            if (docNodes.getLength() > 0) {
                kmlProjectName = getElementValue((Element) docNodes.item(0), "name");
            }
            
            if (kmlProjectName == null || kmlProjectName.isEmpty()) {
                kmlProjectName = projectName != null && !projectName.isEmpty() ? projectName : file.getOriginalFilename();
            }

            Project project = new Project();
            project.setName(kmlProjectName);
            project.setStartDate(new Date());
            project.setCreatedBy(currentUserEmail);
            project = projectRepository.save(project);

            processKmlPlacemarks(doc, project, styles, kmzContent.getResources());
            
            return projectMapper.toDto(project);

        } catch (Exception e) {
            log.error("Error importing file", e);
            throw new RuntimeException("Failed to import: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ProjectDto importKmlToProject(String currentUserEmail, UUID projectId, MultipartFile file) {
        log.info("Starting import for user: {}, file: {}, into existing project: {}", currentUserEmail, file.getOriginalFilename(), projectId);

        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
            
            KmzContent kmzContent = getKmzContent(file);
            Document doc = parseKmlFromStream(kmzContent.getKmlStream());
            Map<String, Map<String, Object>> styles = parseStyles(doc, kmzContent.getResources());
            processKmlPlacemarks(doc, project, styles, kmzContent.getResources());
            
            return projectMapper.toDto(project);

        } catch (Exception e) {
            log.error("Error importing to project " + projectId, e);
            throw new RuntimeException("Failed to import: " + e.getMessage());
        }
    }

    private KmzContent getKmzContent(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".kmz")) {
            return unzippingKmz(file.getInputStream());
        } else {
            return new KmzContent(file.getInputStream(), new HashMap<>());
        }
    }

    private KmzContent unzippingKmz(InputStream inputStream) throws Exception {
        Map<String, byte[]> resources = new HashMap<>();
        byte[] kmlData = null;

        try (ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                byte[] data = baos.toByteArray();

                if (entry.getName().toLowerCase().endsWith(".kml") && kmlData == null) {
                    kmlData = data;
                } else {
                    resources.put(entry.getName(), data);
                    // Также сохраняем с нормализованным путем (без начальных слэшей)
                    String normalizedPath = entry.getName().replace("\\", "/");
                    if (normalizedPath.startsWith("/")) {
                        normalizedPath = normalizedPath.substring(1);
                    }
                    resources.put(normalizedPath, data);
                }
            }
        }

        if (kmlData == null) {
            throw new RuntimeException("No KML file found in KMZ archive");
        }

        return new KmzContent(new java.io.ByteArrayInputStream(kmlData), resources);
    }

    private static class KmzContent {
        private final InputStream kmlStream;
        private final Map<String, byte[]> resources;

        public KmzContent(InputStream kmlStream, Map<String, byte[]> resources) {
            this.kmlStream = kmlStream;
            this.resources = resources;
        }

        public InputStream getKmlStream() { return kmlStream; }
        public Map<String, byte[]> getResources() { return resources; }
    }

    private Document parseKmlFromStream(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(inputStream);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private Document parseKmlFile(MultipartFile file) throws Exception {
        return parseKmlFromStream(file.getInputStream());
    }

    private void processKmlPlacemarks(Document doc, Project project, Map<String, Map<String, Object>> styles, Map<String, byte[]> resources) throws Exception {
        NodeList placemarks = doc.getElementsByTagNameNS("*", "Placemark");
        log.info("Found {} placemarks in KML", placemarks.getLength());
        KMLReader kmlReader = new KMLReader(geometryFactory);

        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            String name = getElementValue(placemark, "name");
            String description = getElementValue(placemark, "description");
            String styleUrl = getElementValue(placemark, "styleUrl");
            
            Map<String, Object> characteristics = null;
            if (styleUrl != null) {
                // Убираем '#' из начала styleUrl
                String styleId = styleUrl.startsWith("#") ? styleUrl.substring(1) : styleUrl;
                Map<String, Object> styleData = styles.get(styleId);
                if (styleData != null) {
                    characteristics = new java.util.HashMap<>();
                    characteristics.put("style", styleData);
                }
            }

            // Ищем геометрический элемент внутри Placemark (Point, LineString, Polygon, MultiGeometry)
            Element geometryElement = findGeometryElement(placemark);
            if (geometryElement != null) {
                // Очистка координат от лишних пробелов и переносов строк перед парсингом
                cleanCoordinates(geometryElement);

                String geometryXml = elementToString(geometryElement);
                try {
                    Geometry geometry = kmlReader.read(geometryXml);

                    if (geometry instanceof Point) {
                        savePoint(project, name, description, (Point) geometry, characteristics);
                    } else if (geometry instanceof LineString || geometry instanceof MultiLineString) {
                        saveMultiline(project, name, description, geometry, characteristics);
                    } else if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
                        savePolygon(project, name, description, geometry, characteristics);
                    } else if (geometry instanceof GeometryCollection) {
                        processGeometryCollection(project, name, description, (GeometryCollection) geometry, characteristics);
                    }
                } catch (Exception e) {
                    log.error("Failed to parse or save geometry for placemark: {}. Error: {}", name, e.getMessage(), e);
                }
            }
        }
    }

    private void cleanCoordinates(Element element) {
        NodeList coordsList = element.getElementsByTagName("coordinates");
        for (int i = 0; i < coordsList.getLength(); i++) {
            Element coordsElem = (Element) coordsList.item(i);
            String original = coordsElem.getTextContent();
            if (original != null) {
                // 1. Удаляем пробелы вокруг запятых
                // 2. Заменяем любые последовательности пробельных символов (переносы, табуляции) на один пробел
                // 3. Убираем пробелы в начале и конце
                String cleaned = original.replaceAll("\\s*,\\s*", ",")
                                         .replaceAll("\\s+", " ")
                                         .trim();
                coordsElem.setTextContent(cleaned);
            }
        }
    }

    private void processGeometryCollection(Project project, String name, String description, GeometryCollection collection, Map<String, Object> characteristics) {
        for (int i = 0; i < collection.getNumGeometries(); i++) {
            Geometry geom = collection.getGeometryN(i);
            if (geom instanceof Point) {
                savePoint(project, name, description, (Point) geom, characteristics);
            } else if (geom instanceof LineString || geom instanceof MultiLineString) {
                saveMultiline(project, name, description, geom, characteristics);
            } else if (geom instanceof Polygon || geom instanceof MultiPolygon) {
                savePolygon(project, name, description, geom, characteristics);
            }
        }
    }

    private void savePoint(Project project, String name, String description, Point geom, Map<String, Object> characteristics) {
        if (characteristics == null) {
            characteristics = new HashMap<>();
        }
        characteristics.put("type", "other");
        ProjectPoint point = new ProjectPoint();
        point.setProject(project);
        point.setName(name);
        point.setDescription(description);
        point.setStatus(Status.COMPLETED);
        Point geom3D = (Point) GeometryUtils.ensure3D(geom);
        point.setGeom(geom3D);
        point.setCharacteristics(characteristics);
        projectPointRepository.save(point);
        Map<String, Object> payload = objectMapper.convertValue(point, Map.class);
        payload.put("type", "point");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);
    }

    private void saveMultiline(Project project, String name, String description, Geometry geom, Map<String, Object> characteristics) {
        ProjectMultiline line = new ProjectMultiline();
        line.setProject(project);
        line.setName(name);
        line.setDescription(description);
        line.setStatus(Status.COMPLETED);
        line.setCharacteristics(characteristics);

        Geometry geom3D = GeometryUtils.ensure3D(geom);
        if (geom3D instanceof MultiLineString) {
            line.setGeom((MultiLineString) geom3D);
        } else if (geom3D instanceof LineString) {
            line.setGeom(geometryFactory.createMultiLineString(new LineString[]{(LineString) geom3D}));
        }

        projectMultilineRepository.save(line);
        Map<String, Object> payload = objectMapper.convertValue(line, Map.class);
        payload.put("type", "multiline");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);
    }

    private void savePolygon(Project project, String name, String description, Geometry geom, Map<String, Object> characteristics) {
        ProjectPolygon polygon = new ProjectPolygon();
        polygon.setProject(project);
        polygon.setName(name);
        polygon.setDescription(description);
        polygon.setStatus(Status.COMPLETED);
        polygon.setCharacteristics(characteristics);

        Geometry geom3D = GeometryUtils.ensure3D(geom);
        if (geom3D instanceof Polygon) {
            polygon.setGeom((Polygon) geom3D);
        } else if (geom3D instanceof MultiPolygon) {
            if (geom3D.getNumGeometries() > 0) {
                polygon.setGeom((Polygon) geom3D.getGeometryN(0));
            }
        }

        projectPolygonRepository.save(polygon);
        Map<String, Object> payload = objectMapper.convertValue(polygon, Map.class);
        payload.put("type", "polygon");
        kafkaProducerService.sendGeoObjectEvent(payload, GeoObjectEvent.EventType.CREATED);
    }

    private String getElementValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
    }

    private Element findGeometryElement(Element placemark) {
        String[] geoTags = {"Point", "LineString", "Polygon", "MultiGeometry"};
        for (String tag : geoTags) {
            NodeList list = placemark.getElementsByTagName(tag);
            if (list.getLength() > 0) {
                return (Element) list.item(0);
            }
        }
        return null;
    }

    private String elementToString(Element el) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(el), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    private Map<String, Map<String, Object>> parseStyles(Document doc, Map<String, byte[]> resources) {
        Map<String, Map<String, Object>> styleMap = new java.util.HashMap<>();

        // Сначала парсим обычные стили <Style>
        NodeList styles = doc.getElementsByTagNameNS("*", "Style");
        for (int i = 0; i < styles.getLength(); i++) {
            Element styleElem = (Element) styles.item(i);
            String id = styleElem.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                styleMap.put(id, extractStyleProperties(styleElem, resources));
            }
        }

        // Затем парсим <StyleMap>, которые ссылаются на <Style>
        NodeList styleMaps = doc.getElementsByTagNameNS("*", "StyleMap");
        for (int i = 0; i < styleMaps.getLength(); i++) {
            Element styleMapElem = (Element) styleMaps.item(i);
            String id = styleMapElem.getAttribute("id");
            if (id != null && !id.isEmpty()) {
                // Для простоты берем стиль 'normal' из StyleMap
                NodeList pairs = styleMapElem.getElementsByTagNameNS("*", "Pair");
                for (int j = 0; j < pairs.getLength(); j++) {
                    Element pair = (Element) pairs.item(j);
                    String key = getElementValue(pair, "key");
                    if ("normal".equals(key)) {
                        String styleUrl = getElementValue(pair, "styleUrl");
                        if (styleUrl != null) {
                            String styleId = styleUrl.startsWith("#") ? styleUrl.substring(1) : styleUrl;
                            Map<String, Object> styleProps = styleMap.get(styleId);
                            if (styleProps != null) {
                                styleMap.put(id, styleProps);
                            }
                        }
                    }
                }
            }
        }

        return styleMap;
    }

    private Map<String, Object> extractStyleProperties(Element styleElem, Map<String, byte[]> resources) {
        Map<String, Object> properties = new java.util.HashMap<>();

        // IconStyle
        NodeList iconStyles = styleElem.getElementsByTagNameNS("*", "IconStyle");
        if (iconStyles.getLength() > 0) {
            Map<String, Object> iconProps = new java.util.HashMap<>();
            Element iconStyle = (Element) iconStyles.item(0);
            
            String scale = getElementValue(iconStyle, "scale");
            if (scale != null) iconProps.put("scale", Double.parseDouble(scale));

            String heading = getElementValue(iconStyle, "heading");
            if (heading != null) iconProps.put("heading", Double.parseDouble(heading));

            NodeList icons = iconStyle.getElementsByTagNameNS("*", "Icon");
            if (icons.getLength() > 0) {
                Element icon = (Element) icons.item(0);
                String href = getElementValue(icon, "href");
                if (href != null) {
                    String storedUrl = uploadIcon(href, resources);
                    iconProps.put("url", storedUrl);
                }
            }
            
            NodeList hotSpots = iconStyle.getElementsByTagNameNS("*", "hotSpot");
            if (hotSpots.getLength() > 0) {
                Element hotSpot = (Element) hotSpots.item(0);
                Map<String, Object> hsProps = new java.util.HashMap<>();
                hsProps.put("x", hotSpot.getAttribute("x"));
                hsProps.put("y", hotSpot.getAttribute("y"));
                hsProps.put("xunits", hotSpot.getAttribute("xunits"));
                hsProps.put("yunits", hotSpot.getAttribute("yunits"));
                iconProps.put("hotSpot", hsProps);
            }

            properties.put("icon", iconProps);
        }

        // LineStyle
        NodeList lineStyles = styleElem.getElementsByTagNameNS("*", "LineStyle");
        if (lineStyles.getLength() > 0) {
            Map<String, Object> lineProps = new java.util.HashMap<>();
            Element lineStyle = (Element) lineStyles.item(0);
            
            String color = getElementValue(lineStyle, "color");
            if (color != null) lineProps.put("color", kmlColorToHex(color));

            String width = getElementValue(lineStyle, "width");
            if (width != null) lineProps.put("width", Double.parseDouble(width));

            properties.put("line", lineProps);
        }

        // PolyStyle
        NodeList polyStyles = styleElem.getElementsByTagNameNS("*", "PolyStyle");
        if (polyStyles.getLength() > 0) {
            Map<String, Object> polyProps = new java.util.HashMap<>();
            Element polyStyle = (Element) polyStyles.item(0);
            
            String color = getElementValue(polyStyle, "color");
            if (color != null) polyProps.put("fillColor", kmlColorToHex(color));

            String fill = getElementValue(polyStyle, "fill");
            if (fill != null) polyProps.put("fill", "1".equals(fill));

            String outline = getElementValue(polyStyle, "outline");
            if (outline != null) polyProps.put("outline", "1".equals(outline));

            properties.put("poly", polyProps);
        }
        
        // LabelStyle
        NodeList labelStyles = styleElem.getElementsByTagNameNS("*", "LabelStyle");
        if (labelStyles.getLength() > 0) {
            Map<String, Object> labelProps = new java.util.HashMap<>();
            Element labelStyle = (Element) labelStyles.item(0);
            
            String color = getElementValue(labelStyle, "color");
            if (color != null) labelProps.put("color", kmlColorToHex(color));

            String scale = getElementValue(labelStyle, "scale");
            if (scale != null) labelProps.put("scale", Double.parseDouble(scale));

            properties.put("label", labelProps);
        }

        return properties;
    }

    private String uploadIcon(String href, Map<String, byte[]> resources) {
        if (href == null || href.isEmpty()) return null;
        
        try {
            byte[] imageBytes = null;
            String fileName = null;

            // 1. Сначала ищем в локальных ресурсах (для KMZ)
            String normalizedHref = href.replace("\\", "/");
            if (normalizedHref.startsWith("./")) {
                normalizedHref = normalizedHref.substring(2);
            }
            if (resources.containsKey(normalizedHref)) {
                imageBytes = resources.get(normalizedHref);
                fileName = normalizedHref.substring(normalizedHref.lastIndexOf("/") + 1);
            } 
            // 2. Если не нашли и это HTTP ссылка, скачиваем
            else if (href.startsWith("http")) {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(href, byte[].class);
                imageBytes = response.getBody();
                fileName = href.substring(href.lastIndexOf("/") + 1);
            }

            if (imageBytes != null && fileName != null) {
                // Sanitize filename
                fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                MultipartFile multipartFile = new CustomMultipartFile(fileName, fileName, "image/png", imageBytes);
                var docDto = documentServiceClient.uploadDocument(multipartFile, UUID.randomUUID(), "KML/KMZ Icon: " + fileName, "icon");
                return "/api/documents/public/image/" + docDto.getId();
            }
        } catch (Exception e) {
            log.warn("Failed to download/upload icon: {}. Error: {}", href, e.getMessage());
        }
        return href;
    }

    private String kmlColorToHex(String kmlColor) {
        // KML color format: aabbggrr (hex)
        // CSS color format: #rrggbbaa (hex)
        if (kmlColor == null || kmlColor.length() != 8) return kmlColor;
        
        String a = kmlColor.substring(0, 2);
        String b = kmlColor.substring(2, 4);
        String g = kmlColor.substring(4, 6);
        String r = kmlColor.substring(6, 8);
        
        return "#" + r + g + b + a;
    }

    private static class CustomMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public CustomMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                fos.write(content);
            }
        }
    }
}
