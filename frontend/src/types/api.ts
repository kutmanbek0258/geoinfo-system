export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

export type Status = 'ACTIVE' | 'INACTIVE' | 'DELETED';

export interface Project {
    id: string; // UUID
    name: string;
    description?: string;
}

export interface ImageryLayer {
    id: string; // UUID
    name: string;
    description?: string;
    workspace: string;
    layerName: string;
    serviceUrl: string;
    status: Status;
    style?: string;
    dateCaptured: string; // Date -> string
    crs: string;
}

export interface ProjectPoint {
    id: string;
    projectId: string;
    name: string;
    description?: string;
    status: Status;
    geom: any; // GeoJSON Point
}

export interface ProjectMultiline {
    id: string;
    projectId: string;
    name: string;
    description?: string;
    status: Status;
    geom: any; // GeoJSON MultiLineString
    lengthM?: number;
}

export interface ProjectPolygon {
    id: string;
    projectId: string;
    name: string;
    description?: string;
    status: Status;
    geom: any; // GeoJSON Polygon
    areaM2?: number;
}

export interface Document {
    id: string;
    geoObjectId: string;
    fileName: string;
    mimeType: string;
    fileSizeBytes: number;
    description?: string;
    uploadedByUserId: string;
    uploadDate: string;
    isLatestVersion: boolean;
    tags: { id: number; name: string }[];
}

export interface SearchResult {
    id: string;
    type: string;
    title: string;
    snippet: string;
}