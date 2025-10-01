import { Point, MultiLineString, Polygon } from 'geojson';

export interface Page<T> {
    content: T[];
    pageable: any;
    totalElements: number;
    totalPages: number;
    last: boolean;
}

export interface Project {
    id: string;
    name: string;
    description?: string;
    startDate: string;
    endDate: string;
}

export interface ImageryLayer {
    id: string;
    name: string;
    url: string;
    layer_name: string;
    use_proxy: boolean;
}

// Базовый интерфейс для всех векторных данных
export interface BaseVectorData {
    id: string;
    projectId: string;
    name: string;
    description?: string;
    status: 'IN_PROCESS' | 'COMPLETED' | 'ARCHIVED';
}

export interface ProjectPoint extends BaseVectorData {
    geom: Point;
}

export interface ProjectMultiline extends BaseVectorData {
    geom: MultiLineString;
}

export interface ProjectPolygon extends BaseVectorData {
    geom: Polygon;
}

export interface Tag {
    id: number;
    name: string;
}

export interface Document {
    id: string;
    fileName: string;
    fileType: string;
    fileSize: number;
    description: string;
    geoObjectId: string;
    tags: Tag[];
    createdAt: string;
}

export interface SearchResult {
    id: string;
    type: 'point' | 'multiline' | 'polygon' | 'document';
    name: string;
    highlight?: {
        [key: string]: string[];
    };
}
