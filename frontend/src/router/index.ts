import { createRouter, createWebHistory } from 'vue-router';
import MainRoutes from './MainRoutes';
import AuthRoutes from './AuthRoutes';
import LoginService from '@/services/login.service'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/:pathMatch(.*)*',
            component: () => import('@/views/pages/Error404.vue')
        },
        MainRoutes,
        AuthRoutes
    ]
});

router.beforeEach((to, from, next) => {
    // redirect to login page if not logged in and trying to access a restricted page
    const publicPages = ['/code', '/auth/login', '/auth/register'];
    const authRequired = !publicPages.includes(to.path);
    const loggedIn = localStorage.getItem('loggedIn');

    if (!loggedIn && authRequired) {
        LoginService.login();
        return; // No need to call next() due to full page redirection
    }

    if (to.path === '/code' && to.query.code) {
        const code = Array.isArray(to.query.code) ? to.query.code[0] : to.query.code;
        if (code) {
            LoginService.getTokens(code).then(() => {
                console.log('Logged in!');
            });
        }
    }

    next();
});

export default router;

