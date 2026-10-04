import {
  Component, Input, Output, EventEmitter, OnChanges, SimpleChanges,
  ElementRef, HostListener, forwardRef, ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Station } from '../../../core/models';

/**
 * Typeahead combobox for picking a station.
 * Drop-in replacement for <select [(ngModel)]="stationId"> thanks to ControlValueAccessor.
 * Searches across name, region, manager, address and id.
 */
@Component({
  selector: 'app-station-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './station-picker.component.html',
  styleUrls: ['./station-picker.component.scss'],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => StationPickerComponent),
    multi: true
  }]
})
export class StationPickerComponent implements ControlValueAccessor, OnChanges {

  @Input() stations: Station[] = [];
  @Input() placeholder = 'Rechercher une station...';
  @Input() allowAll = false;
  @Input() allLabel = 'Toutes les stations';
  @Input() disabled = false;
  @Input() width: string = '260px';
  @Output() stationChange = new EventEmitter<number | null>();

  @ViewChild('inputEl') inputEl?: ElementRef<HTMLInputElement>;

  query = '';
  open = false;
  highlightedIndex = 0;
  selectedId: number | null = null;
  filtered: Station[] = [];

  private onChange: (val: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  constructor(private host: ElementRef) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['stations']) {
      this.recomputeFiltered();
      this.syncDisplayFromId();
    }
    if (changes['disabled'] && this.disabled) this.open = false;
  }

  // ── ControlValueAccessor ──
  writeValue(val: number | null): void {
    this.selectedId = val ?? null;
    this.syncDisplayFromId();
  }
  registerOnChange(fn: (v: number | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.disabled = disabled; }

  // ── UI handlers ──
  onInput(): void {
    this.open = true;
    this.highlightedIndex = 0;
    this.recomputeFiltered();
    if (!this.query.trim() && this.selectedId !== null) {
      this.selectedId = null;
      this.onChange(null);
      this.stationChange.emit(null);
    }
  }

  onFocus(): void {
    if (this.disabled) return;
    this.open = true;
    this.recomputeFiltered();
  }

  onBlur(): void { this.onTouched(); }

  onKeyDown(evt: KeyboardEvent): void {
    if (this.disabled) return;
    const list = this.getKeyboardList();

    if (evt.key === 'ArrowDown') {
      this.open = true;
      this.highlightedIndex = Math.min(this.highlightedIndex + 1, list.length - 1);
      evt.preventDefault();
    } else if (evt.key === 'ArrowUp') {
      this.highlightedIndex = Math.max(0, this.highlightedIndex - 1);
      evt.preventDefault();
    } else if (evt.key === 'Enter') {
      if (!this.open) { this.open = true; return; }
      const choice = list[this.highlightedIndex];
      if (choice === 'ALL') this.select(null);
      else if (choice) this.select(choice as Station);
      evt.preventDefault();
    } else if (evt.key === 'Escape') {
      this.open = false;
      this.syncDisplayFromId();
      evt.preventDefault();
    }
  }

  select(station: Station | null): void {
    this.selectedId = station ? station.id : null;
    this.query = station ? station.name : '';
    this.open = false;
    this.onChange(this.selectedId);
    this.stationChange.emit(this.selectedId);
  }

  clear(evt: Event): void {
    evt.stopPropagation();
    this.query = '';
    this.selectedId = null;
    this.onChange(null);
    this.stationChange.emit(null);
    this.recomputeFiltered();
    this.inputEl?.nativeElement.focus();
    this.open = true;
  }

  @HostListener('document:click', ['$event'])
  onDocClick(e: MouseEvent): void {
    if (!this.host.nativeElement.contains(e.target)) {
      this.open = false;
      this.syncDisplayFromId();
    }
  }

  // ── helpers ──
  private syncDisplayFromId(): void {
    if (this.selectedId === null) { this.query = ''; return; }
    const st = this.stations.find(s => s.id === this.selectedId);
    this.query = st ? st.name : '';
  }

  private recomputeFiltered(): void {
    const q = this.query.trim().toLowerCase();
    if (!q) { this.filtered = [...this.stations]; return; }
    this.filtered = this.stations.filter(s =>
      (s.name && s.name.toLowerCase().includes(q)) ||
      (s.region && s.region.toLowerCase().includes(q)) ||
      (s.managerName && s.managerName.toLowerCase().includes(q)) ||
      (s.address && s.address.toLowerCase().includes(q)) ||
      String(s.id).includes(q)
    );
  }

  private getKeyboardList(): (Station | 'ALL')[] {
    const list: (Station | 'ALL')[] = [];
    if (this.allowAll) list.push('ALL');
    list.push(...this.filtered);
    return list;
  }

  rowIndex(i: number): number { return this.allowAll ? i + 1 : i; }

  highlight(text: string | undefined | null): string {
    if (!text) return '';
    const safe = this.escapeHtml(text);
    const q = this.query.trim();
    if (!q) return safe;
    const re = new RegExp(`(${this.escapeRegex(q)})`, 'ig');
    return safe.replace(re, '<mark>$1</mark>');
  }

  private escapeRegex(str: string): string {
    return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  private escapeHtml(str: string): string {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
}
