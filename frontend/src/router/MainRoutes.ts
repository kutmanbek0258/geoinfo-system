const MainRoutes = {
    path: '/main',
    meta: {
        requiresAuth: true
    },
    redirect: '/main',
    component: () => import('@/layouts/full/FullLayout.vue'),
    children: [
        {
            name: 'Dashboard',
            path: '/',
            component: () => import('@/views/dashboard/index.vue')
        },
        {
            name: 'Projects',
            path: '/ui/projects',
            component: () => import('@/views/components/Projects.vue')
        },
        {
            name: 'Imagery layers',
            path: '/ui/layers',
            component: () => import('@/views/components/RasterLayers.vue')
        },
        {
            name: 'Terrain layers',
            path: '/ui/terrain',
            component: () => import('@/views/components/TerrainLayers.vue')
        },
        {
            name: '3D Tiles layers',
            path: '/ui/3dtiles',
            component: () => import('@/views/components/ThreeDTilesLayers.vue')
        },
        {
            name: 'Process Jobs',
            path: '/ui/jobs',
            component: () => import('@/views/components/ProcessJobs.vue')
        },
        {
            name: 'Interpolation styles',
            path: '/ui/styles',
            component: () => import('@/views/components/RasterStyles.vue')
        },
        {
            name: 'Icons',
            path: '/icons',
            component: () => import('@/views/pages/Icons.vue')
        },
        {
            name: 'Starter',
            path: '/sample-page',
            component: () => import('@/views/pages/SamplePage.vue')
        },
        {
            name: 'ProjectMapView',
            path: '/projects/:id',
            meta: { fullWidth: true },
            component: () => import('@/views/ProjectMapView.vue')
        },
        {
            name: 'OnlyOfficeEditor',
            path: '/editor/:id',
            component: () => import('@/views/pages/OnlyOfficeEditor.vue')
        }
    ]
};

export default MainRoutes;
