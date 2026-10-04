import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DemoSwitcherComponent } from './core/demo/demo-switcher.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DemoSwitcherComponent],
  template: `
  <router-outlet></router-outlet>
  <app-demo-switcher />
  `
})
export class AppComponent {}
