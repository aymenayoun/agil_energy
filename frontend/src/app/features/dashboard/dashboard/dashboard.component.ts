import { Component, OnInit, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { DashboardService } from '../../../core/services/dashboard.service';
import { Dashboard, Tank, Alert, StationSummary } from '../../../core/models';
import Chart from 'chart.js/auto';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, AfterViewInit {

  @ViewChild('ruptureChart') ruptureChartRef!: ElementRef<HTMLCanvasElement>;

  dashboard: Dashboard | null = null;
  loading = true;
  private chart: Chart | null = null;

  constructor(private dashboardService: DashboardService, private router: Router) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  ngAfterViewInit(): void {}

  loadDashboard(): void {
    this.dashboardService.getDashboard().subscribe({
      next: (res) => {
        if (res.success) {
          this.dashboard = res.data;
          this.loading = false;
          setTimeout(() => this.buildChart(), 100);
        }
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  buildChart(): void {
    if (!this.ruptureChartRef || !this.dashboard) return;

    const summaries = this.dashboard.stationSummaries;
    const labels = summaries.map(s => s.stationName);
    const data = summaries.map(s => s.daysBeforeRupture ?? 0);
    const colors = data.map(d =>
      d <= 3 ? '#EB5757' : d <= 7 ? '#F2994A' : '#27AE60'
    );

    if (this.chart) this.chart.destroy();

    // Set global Chart.js defaults for dark theme
    Chart.defaults.color = '#A0A0A0';
    Chart.defaults.borderColor = '#2A2A2A';

    this.chart = new Chart(this.ruptureChartRef.nativeElement, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Jours avant rupture',
          data: data,
          backgroundColor: colors,
          borderRadius: 6,
          borderSkipped: false
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: '#1E1E1E',
            titleColor: '#E8E8E8',
            bodyColor: '#A0A0A0',
            borderColor: '#333',
            borderWidth: 1,
            cornerRadius: 8,
            padding: 10,
            displayColors: false,
            callbacks: {
              label: (ctx: any) => `${ctx.parsed.y} jours`
            }
          }
        },
        scales: {
          y: {
            beginAtZero: true,
            title: {
              display: true,
              text: 'Jours',
              color: '#666'
            },
            grid: {
              color: '#1E1E1E'
            },
            ticks: {
              color: '#666'
            }
          },
          x: {
            grid: {
              display: false
            },
            ticks: {
              color: '#888',
              maxRotation: 45
            }
          }
        }
      }
    });
  }

  // ==================== NAVIGATION (deep-link with pre-filter) ====================

  goToStations(stationId?: number): void {
    this.router.navigate(['/stations'], {
      queryParams: stationId ? { stationId } : {}
    });
  }

  goToStocks(stationId?: number): void {
    this.router.navigate(['/stocks'], {
      queryParams: stationId ? { stationId } : {}
    });
  }

  goToAlerts(stationId?: number, severity?: string): void {
    const queryParams: any = {};
    if (stationId) queryParams.stationId = stationId;
    if (severity) queryParams.severity = severity;
    this.router.navigate(['/alerts'], { queryParams });
  }

  getDaysClass(days: number): string {
    if (days <= 3) return 'badge badge-danger';
    if (days <= 7) return 'badge badge-warning';
    return 'badge badge-success';
  }

  getSeverityBadge(severity: string): string {
    switch (severity) {
      case 'HIGH': return 'danger';
      case 'MEDIUM': return 'warning';
      case 'LOW': return 'info';
      default: return 'neutral';
    }
  }
}
