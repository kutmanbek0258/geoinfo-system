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
    isSaved?: boolean;
}

export interface ColorMapEntry {
    color: string;
    quantity: number;
    opacity: number;
    label?: string;
}

export interface RasterStyle {
    id: string;
    name: string;
    title: string;
    type: string;
    config: ColorMapEntry[];
    isSystem: boolean;
    createdDate?: string;
    lastModifiedDate?: string;
}

export interface ImageryLayer {
    id: string; // UUID
    projectId?: string;
    name: string;
    description?: string;
    layerName?: string;
    status: Status;
    style?: RasterStyle;
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
    isSaved?: boolean;
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
    isSaved?: boolean;
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
    isSaved?: boolean;
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
    projectId?: string;
    title: string;
    description?: string;
    terrainUrl: string;
    cogObjectKey?: string;
    cogUrl?: string;
    status: string;
    isVisible: boolean;
}

export interface TerrainJob {
    id: string;
    projectId?: string;
    name: string;
    status: string;
    taskType?: string;
    characteristics?: Record<string, any>;
    errorMessage?: string;
}

export type AnalysisTaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface AnalysisTask {
    id: string;
    pluginName: string;
    status: AnalysisTaskStatus;
    inputParams: Record<string, any>;
    s3InputPaths: Record<string, string>;
    s3OutputPaths: Record<string, string>;
    errorMessage?: string;
    userId?: string;
    projectId?: string;
}

export interface CreateAnalysisTaskDto {
    pluginName: string;
    projectId?: string;
    inputs: Record<string, AnalysisDataSource>;
    parameters: Record<string, any>;
}

export interface AnalysisDataSource {
    type: 'IMAGERY_LAYER' | 'VECTOR_LAYER' | 'TERRAIN_LAYER' | 'PREVIOUS_TASK_RESULT' | 'DIRECT_S3';
    id?: string;
    taskId?: string;
    outputKey?: string;
    s3Url?: string;
}
