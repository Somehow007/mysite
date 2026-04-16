import { createRouter, createWebHistory } from 'vue-router'
import BlogHome from '../views/BlogHome.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: BlogHome,
    },
    {
      path: '/post/:id',
      name: 'BlogPost',
      component: () => import('../views/BlogPost.vue'),
    },
    {
      path: '/post/create',
      name: 'CreatePost',
      component: () => import('../views/CreatePost.vue'),
    },
    {
      path: '/post/edit/:id',
      name: 'EditPost',
      component: () => import('../views/CreatePost.vue'),
    },
    {
      path: '/author/:slug',
      name: 'AuthorView',
      component: () => import('../views/AuthorView.vue'),
    },
    {
      path: '/tag/:slug',
      name: 'TagView',
      component: () => import('../views/TagView.vue'),
    },
    {
      path: '/page/:slug',
      name: 'PageView',
      component: () => import('../views/PageView.vue'),
    },
    {
      path: '/page/:page(\\d+)',
      name: 'BlogHomePage',
      component: () => import('../views/BlogHome.vue'),
    },
    {
      path: '/login',
      name: 'LoginPage',
      component: () => import('../components/LoginPage.vue'),
    },
    {
      path: '/register',
      name: 'RegisterPage',
      component: () => import('../views/RegisterPage.vue'),
    },
    {
      path: '/about',
      name: 'AboutView',
      component: () => import('../views/AboutView.vue'),
    },
  ],
})

export default router
