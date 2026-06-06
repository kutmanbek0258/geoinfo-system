export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

export type Status = 'ACTIVE' | 'INACTIVE' | 'DELETED' | 'COMPLETED' | 'IN_PROCESS' | 'REJECTED';

export interface Project {
    id: string; // UUID
    name: string;
    description?: string;
    bbox?: GeoGeometry;
}

export interface GeoFolder {
    id: string;
    projectId: string;
    parentId?: string;
    name: string;
    description?: string;
    characteristics?: Record<string, any>;
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
    characteristics?: Record<string, any>;
    cogObjectKey?: string;
    bbox?: GeoGeometry;
}

export type GeoGeometry = 
    | { type: 'Point'; coordinates: number[] }
    | { type: 'MultiPoint'; coordinates: number[][] }
    | { type: 'LineString'; coordinates: number[][] }
    | { type: 'MultiLineString'; coordinates: number[][][] }
    | { type: 'Polygon'; coordinates: number[][][] }
    | { type: 'MultiPolygon'; coordinates: number[][][][] };

export interface ProjectPoint {
    id: string;
    projectId: string;
    folderId?: string;
    name: string;
    description?: string;
    status: Status;
    geom: GeoGeometry;
    bbox?: GeoGeometry;
    imageUrl?: string;
    characteristics?: Record<string, any>;
}

export interface ProjectPointSummary extends Omit<ProjectPoint, 'geom'> {}

export interface ProjectMultiline {
    id: string;
    projectId: string;
    folderId?: string;
    name: string;
    description?: string;
    status: Status;
    geom: GeoGeometry;
    bbox?: GeoGeometry;
    lengthM?: number;
    imageUrl?: string;
    characteristics?: Record<string, any>;
}

export interface ProjectMultilineSummary extends Omit<ProjectMultiline, 'geom'> {}

export interface ProjectPolygon {
    id: string;
    projectId: string;
    folderId?: string;
    name: string;
    description?: string;
    status: Status;
    geom: GeoGeometry;
    bbox?: GeoGeometry;
    areaM2?: number;
    imageUrl?: string;
    characteristics?: Record<string, any>;
}

export interface ProjectPolygonSummary extends Omit<ProjectPolygon, 'geom'> {}

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

export interface TerrainLayer {
    id: string;
    title: string;
    description?: string;
    terrainUrl: string;
    status: string;
    isVisible: boolean;
}

export interface TerrainJob {
    id: string;
    name: string;
    status: string;
    taskType?: string;
    characteristics?: Record<string, any>;
    errorMessage?: string;
}
