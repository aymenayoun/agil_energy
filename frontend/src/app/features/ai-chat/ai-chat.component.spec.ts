import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AIChatComponent } from './ai-chat.component';

describe('AIChatComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AIChatComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should start with one welcome message', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    expect(fixture.componentInstance.messages.length).toBe(1);
    expect(fixture.componentInstance.messages[0].role).toBe('assistant');
  });

  it('should default mode to default', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    expect(fixture.componentInstance.mode).toBe('default');
  });

  it('should default loading to false', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    expect(fixture.componentInstance.loading).toBeFalse();
  });

  it('send() should not send empty messages', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    const comp = fixture.componentInstance;
    comp.input = '   ';
    comp.send();
    expect(comp.messages.length).toBe(1); // still just the welcome
  });

  it('send() should not send when loading', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    const comp = fixture.componentInstance;
    comp.input = 'Hello';
    comp.loading = true;
    comp.send();
    expect(comp.messages.length).toBe(1);
  });

  it('clear() should keep only the welcome message', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    const comp = fixture.componentInstance;
    comp.messages.push({ role: 'user', content: 'test' });
    comp.messages.push({ role: 'assistant', content: 'reply' });
    expect(comp.messages.length).toBe(3);

    comp.clear();
    expect(comp.messages.length).toBe(1);
    expect(comp.messages[0].role).toBe('assistant');
  });

  it('onKey should prevent default on Enter without Shift', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    const comp = fixture.componentInstance;
    const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: false });
    spyOn(event, 'preventDefault');
    comp.input = ''; // empty — won't actually send
    comp.onKey(event);
    expect(event.preventDefault).toHaveBeenCalled();
  });

  it('onKey should not prevent default on Shift+Enter', () => {
    const fixture = TestBed.createComponent(AIChatComponent);
    const comp = fixture.componentInstance;
    const event = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true });
    spyOn(event, 'preventDefault');
    comp.onKey(event);
    expect(event.preventDefault).not.toHaveBeenCalled();
  });
});
