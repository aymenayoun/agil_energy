import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * AGIL Energy brand logo.
 *
 * Renders the official AGIL galloping-horse mark inline as SVG so it inherits
 * theme colours through CSS custom properties and stays crisp at any size.
 *
 * Variants:
 *   - 'mark'     → horse only (default). Two-tone: body + white mane/eye.
 *   - 'lockup'   → horse stacked above the "Agil energy" wordmark (login hero).
 *   - 'tile'     → horse on a rounded gold tile (sidebar badge / avatar style).
 *
 * Theming (override on a parent or via [style]):
 *   --agil-horse     body colour      (default #111111; set to var(--accent) for gold)
 *   --agil-mane      mane + eye        (default #FFFFFF)
 *   --agil-wordmark  "Agil" text       (default #FFFFFF)
 *   --agil-red       accent dot/energy (default #D42138)
 *
 * Usage:
 *   <app-agil-logo variant="mark" [size]="42"></app-agil-logo>
 *   <app-agil-logo variant="lockup" [width]="220"></app-agil-logo>
 *   <app-agil-logo variant="tile" [size]="42"></app-agil-logo>
 */
@Component({
  selector: 'app-agil-logo',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './agil-logo.component.html',
  styleUrls: ['./agil-logo.component.scss']
})
export class AgilLogoComponent {
  /** 'mark' (horse), 'lockup' (horse + wordmark), or 'tile' (horse on gold tile). */
  @Input() variant: 'mark' | 'lockup' | 'tile' = 'mark';

  /** Square size in px for 'mark'/'tile' variants (sets width & height of the tile/box). */
  @Input() size = 42;

  /** Explicit width in px for the 'lockup' variant (height scales with aspect ratio). */
  @Input() width = 200;

  /** Accessible label. */
  @Input() label = 'AGIL Energy';
}
