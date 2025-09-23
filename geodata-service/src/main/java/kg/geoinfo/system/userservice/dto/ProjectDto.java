package kg.geoinfo.system.userservice.dto;

import kg.geoinfo.system.userservice.references.annotation.CheckDate;

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

    public ProjectDto() {
    }

    public void setId(java.util.UUID id) {
        this.id = id;
    }

    public java.util.UUID getId() {
        return this.id;
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