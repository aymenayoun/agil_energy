import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { defaultRouteGuard } from './core/guards/default-route.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./features/auth/reset-password/reset-password.component').then(m => m.ResetPasswordComponent)
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/layout.component').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard/dashboard.component').then(m => m.DashboardComponent),
        data: { roles: ['ADMIN', 'MANAGER'] }
      },
      {
        path: 'stations',
        loadComponent: () => import('./features/stations/station-list/station-list.component').then(m => m.StationListComponent)
      },
      {
        path: 'fuel-types',
        loadComponent: () => import('./features/fuel-types/fuel-type-list/fuel-type-list.component')
          .then(m => m.FuelTypeListComponent),
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'sales',
        loadComponent: () => import('./features/sales/sale-list/sale-list.component').then(m => m.SaleListComponent)
      },
      {
        path: 'sales/new',
        loadComponent: () => import('./features/sales/sale-form/sale-form.component').then(m => m.SaleFormComponent)
      },
      {
        path: 'stocks',
        loadComponent: () => import('./features/stocks/stock-list/stock-list.component').then(m => m.StockListComponent)
      },
      {
        path: 'alerts',
        loadComponent: () => import('./features/alerts/alert-list/alert-list.component').then(m => m.AlertListComponent)
      },
      {
        path: 'alerts/anomalies',
        loadComponent: () => import('./features/alerts/anomaly-list/anomaly-list.component').then(m => m.AnomalyListComponent)
      },
      {
        path: 'deliveries',
        loadComponent: () => import('./features/deliveries/delivery-list/delivery-list.component')
          .then(m => m.DeliveryListComponent)
      },
      {
        path: 'users',
        loadComponent: () => import('./features/users/user-list/user-list.component')
          .then(m => m.UserListComponent),
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'audit-log',
        loadComponent: () => import('./features/audit-log/audit-log.component').then(m => m.AuditLogComponent),
        data: { roles: ['ADMIN'] }
      },
      {
        path: 'predictions',
        loadComponent: () => import('./features/predictions/prediction-view/prediction-view.component').then(m => m.PredictionViewComponent),
        data: { roles: ['ADMIN', 'MANAGER'] }
      },
      {
        path: 'predictions/regions',
        loadComponent: () => import('./features/predictions/region-prediction-view/region-prediction-view.component').then(m => m.RegionPredictionViewComponent),
        data: { roles: ['ADMIN', 'MANAGER'] }
      },
      {
        path: 'predictions/map',
        loadComponent: () => import('./features/predictions/region-map-view/region-map-view.component').then(m => m.RegionMapViewComponent),
        data: { roles: ['ADMIN', 'MANAGER'] }
      },
      {
        path: 'ai-assistant',
        loadComponent: () => import('./features/ai-chat/ai-chat.component').then(m => m.AIChatComponent),
        data: { roles: ['ADMIN'] }
      },
      {
        path: '',
        canActivate: [defaultRouteGuard],
        // This guard always redirects — component is never rendered
        loadComponent: () => import('./features/dashboard/dashboard/dashboard.component').then(m => m.DashboardComponent),
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'login'
  }
];
