package kg.geoinfo.system.geodataservice.service.impl;

import kg.geoinfo.system.geodataservice.dto.ProjectDto;
import kg.geoinfo.system.geodataservice.mapper.ProjectMapper;
import kg.geoinfo.system.geodataservice.models.*;
import kg.geoinfo.system.geodataservice.models.enums.Status;
import kg.geoinfo.system.geodataservice.repository.*;
import kg.geoinfo.system.geodataservice.service.KmlImportService;
import kg.geoinfo.system.geodataservice.util.GeometryUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.kml.KMLReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.io.StringWriter;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KmlImportServiceImpl implements KmlImportService {

    private final ProjectRepository projectRepository;
    private final ProjectPointRepository projectPointRepository;
    private final ProjectMultilineRepository projectMultilineRepository;
    private final ProjectPolygonRepository projectPolygonRepository;
    private final ProjectMapper projectMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    @Transactional
    public ProjectDto importKml(String currentUserEmail, MultipartFile file, String projectName) {
        log.info("Starting KML import for user: {}, file: {}, project: {}", currentUserEmail, file.getOriginalFilename(), projectName);

        try {
            Document doc = parseKmlFile(file);
            
            // Попытка получить имя проекта из тега <name> внутри <Document> или <Folder>
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

            processKmlPlacemarks(doc, project);
            
            return projectMapper.toDto(project);

        } catch (Exception e) {
            log.error("Error parsing KML file", e);
            throw new RuntimeException("Failed to import KML: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ProjectDto importKmlToProject(String currentUserEmail, UUID projectId, MultipartFile file) {
        log.info("Starting KML import for user: {}, file: {}, into existing project: {}", currentUserEmail, file.getOriginalFilename(), projectId);

        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
            
            // Проверка прав доступа может быть добавлена здесь, если требуется

            Document doc = parseKmlFile(file);
            processKmlPlacemarks(doc, project);
            
            return projectMapper.toDto(project);

        } catch (Exception e) {
            log.error("Error importing KML to project " + projectId, e);
            throw new RuntimeException("Failed to import KML: " + e.getMessage());
        }
    }

    private Document parseKmlFile(MultipartFile file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file.getInputStream());
        doc.getDocumentElement().normalize();
        return doc;
    }

    private void processKmlPlacemarks(Document doc, Project project) throws Exception {
        NodeList placemarks = doc.getElementsByTagNameNS("*", "Placemark");
        log.info("Found {} placemarks in KML", placemarks.getLength());
        KMLReader kmlReader = new KMLReader(geometryFactory);

        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            String name = getElementValue(placemark, "name");
            String description = getElementValue(placemark, "description");

            // Ищем геометрический элемент внутри Placemark (Point, LineString, Polygon, MultiGeometry)
            Element geometryElement = findGeometryElement(placemark);
            if (geometryElement != null) {
                // Очистка координат от лишних пробелов и переносов строк перед парсингом
                cleanCoordinates(geometryElement);

                String geometryXml = elementToString(geometryElement);
                try {
                    Geometry geometry = kmlReader.read(geometryXml);

                    if (geometry instanceof Point) {
                        savePoint(project, name, description, (Point) geometry);
                    } else if (geometry instanceof LineString || geometry instanceof MultiLineString) {
                        saveMultiline(project, name, description, geometry);
                    } else if (geometry instanceof Polygon || geometry instanceof MultiPolygon) {
                        savePolygon(project, name, description, geometry);
                    } else if (geometry instanceof GeometryCollection) {
                        processGeometryCollection(project, name, description, (GeometryCollection) geometry);
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

    private void processGeometryCollection(Project project, String name, String description, GeometryCollection collection) {
        for (int i = 0; i < collection.getNumGeometries(); i++) {
            Geometry geom = collection.getGeometryN(i);
            if (geom instanceof Point) {
                savePoint(project, name, description, (Point) geom);
            } else if (geom instanceof LineString || geom instanceof MultiLineString) {
                saveMultiline(project, name, description, geom);
            } else if (geom instanceof Polygon || geom instanceof MultiPolygon) {
                savePolygon(project, name, description, geom);
            }
        }
    }

    private void savePoint(Project project, String name, String description, Point geom) {
        ProjectPoint point = new ProjectPoint();
        point.setProject(project);
        point.setName(name);
        point.setDescription(description);
        point.setStatus(Status.COMPLETED);
        Point geom3D = (Point) GeometryUtils.ensure3D(geom);
        point.setGeom(geom3D);
        projectPointRepository.save(point);
    }

    private void saveMultiline(Project project, String name, String description, Geometry geom) {
        ProjectMultiline line = new ProjectMultiline();
        line.setProject(project);
        line.setName(name);
        line.setDescription(description);
        line.setStatus(Status.COMPLETED);

        Geometry geom3D = GeometryUtils.ensure3D(geom);
        if (geom3D instanceof MultiLineString) {
            line.setGeom((MultiLineString) geom3D);
        } else if (geom3D instanceof LineString) {
            line.setGeom(geometryFactory.createMultiLineString(new LineString[]{(LineString) geom3D}));
        }

        projectMultilineRepository.save(line);
    }

    private void savePolygon(Project project, String name, String description, Geometry geom) {
        ProjectPolygon polygon = new ProjectPolygon();
        polygon.setProject(project);
        polygon.setName(name);
        polygon.setDescription(description);
        polygon.setStatus(Status.COMPLETED);

        Geometry geom3D = GeometryUtils.ensure3D(geom);
        if (geom3D instanceof Polygon) {
            polygon.setGeom((Polygon) geom3D);
        } else if (geom3D instanceof MultiPolygon) {
            if (geom3D.getNumGeometries() > 0) {
                polygon.setGeom((Polygon) geom3D.getGeometryN(0));
            }
        }

        projectPolygonRepository.save(polygon);
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
}
