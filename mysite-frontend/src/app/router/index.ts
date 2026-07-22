import { createRouter, createWebHistory } from 'vue-router'
import { setRedirectUrl } from '@/utils/redirect'
import { getItem } from '@/utils/storage'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/app/layouts/DefaultLayout.vue'),
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/BlogHome.vue'),
        },
        {
          path: 'page/:page',
          name: 'home-page',
          component: () => import('@/views/BlogHome.vue'),
        },
        {
          path: 'post/:id',
          name: 'post',
          component: () => import('@/views/BlogPost.vue'),
        },
        {
          path: 'category/:slug',
          name: 'category',
          component: () => import('@/views/CategoryView.vue'),
        },
        {
          path: 'tag/:slug',
          name: 'tag',
          component: () => import('@/views/TagView.vue'),
        },
        {
          path: 'archive',
          name: 'archive',
          component: () => import('@/views/ArchiveView.vue'),
        },
        {
          path: 'about',
          name: 'about',
          component: () => import('@/views/AboutView.vue'),
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/SearchView.vue'),
        },
        {
          path: 'collection/:id',
          name: 'collection',
          component: () => import('@/views/CollectionView.vue'),
        },
        {
          path: 'collections',
          name: 'collections-list',
          component: () => import('@/views/CollectionsView.vue'),
        },
        {
          path: 'favorites',
          name: 'favorites',
          component: () => import('@/views/FavoritesView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/views/LoginView.vue'),
          meta: { isLoginPage: true },
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/views/RegisterView.vue'),
        },
      ],
    },
    {
      path: '/dashboard',
      component: () => import('@/app/layouts/DashboardLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
        },
        {
          path: 'posts/new',
          name: 'post-new',
          component: () => import('@/views/PostEditorView.vue'),
        },
        {
          path: 'posts/:id/edit',
          name: 'post-edit',
          component: () => import('@/views/PostEditorView.vue'),
        },
        {
          path: 'categories',
          name: 'categories',
          component: () => import('@/views/CategoryManageView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'tags',
          name: 'tags',
          component: () => import('@/views/TagManageView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'images',
          name: 'images',
          component: () => import('@/views/ImageManagerView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UserManageView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'comments',
          name: 'comments',
          component: () => import('@/views/CommentManageView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'collections',
          name: 'collections-manage',
          component: () => import('@/views/CollectionManageView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'collections/new',
          name: 'collection-new',
          component: () => import('@/views/CollectionEditView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'collections/:id/edit',
          name: 'collection-edit',
          component: () => import('@/views/CollectionEditView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/SettingsView.vue'),
        },
      ],
    },
  ],
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  },
})

router.beforeEach(async (to, _from, next) => {
  const token = getItem<string>('access_token')

  if (to.meta.requiresAuth && !token) {
    setRedirectUrl(to.fullPath)
    next({ name: 'login' })
    return
  }

  if (to.meta.isLoginPage && token) {
    next({ name: 'home' })
    return
  }

  if (to.meta.requiresAdmin && token) {
    const storedRole = getItem<string>('user_role')
    // 兼容旧缓存值 DEVELOPER → ADMIN
    if (storedRole !== 'ADMIN' && storedRole !== 'DEVELOPER') {
      next({ name: 'dashboard' })
      return
    }
  }

  next()
})

export default router
