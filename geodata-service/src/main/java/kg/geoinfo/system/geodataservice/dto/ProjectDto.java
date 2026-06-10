package kg.geoinfo.system.geodataservice.dto;

import kg.geoinfo.system.geodataservice.references.annotation.CheckDate;
import org.locationtech.jts.geom.Geometry;

import java.util.Date;
import java.util.UUID;

public class ProjectDto extends AbstractDto<UUID> {
    private UUID id;
    private String name;
    private String description;
    @CheckDate
    private Date startDate;
    @CheckDate
    private Date endDate;
    private Geometry bbox;

    public ProjectDto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setBbox(Geometry bbox) {
        this.bbox = bbox;
    }

    public Geometry getBbox() {
        return this.bbox;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setStartDate(java.util.Date startDate) {
        this.startDate = startDate;
    }

    public java.util.Date getStartDate() {
        return this.startDate;
    }

    public void setEndDate(java.util.Date endDate) {
        this.endDate = endDate;
    }

    public java.util.Date getEndDate() {
        return this.endDate;
    }
}