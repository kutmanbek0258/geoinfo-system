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
            component: () => import('@/views/components/ImageryLayers.vue')
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
