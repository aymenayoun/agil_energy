import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NotificationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with empty notifications', () => {
    expect(service.notifications()).toEqual([]);
  });

  it('show() should add a notification', () => {
    service.show('hello', 'info', 99999);
    expect(service.notifications().length).toBe(1);
    expect(service.notifications()[0].message).toBe('hello');
    expect(service.notifications()[0].type).toBe('info');
  });

  it('success() should add a success notification', () => {
    service.success('done');
    expect(service.notifications()[0].type).toBe('success');
  });

  it('error() should add an error notification', () => {
    service.error('fail');
    expect(service.notifications()[0].type).toBe('error');
  });

  it('info() should add an info notification', () => {
    service.info('note');
    expect(service.notifications()[0].type).toBe('info');
  });

  it('dismiss() should remove the notification by id', () => {
    service.show('a', 'info', 99999);
    const id = service.notifications()[0].id;
    service.dismiss(id);
    expect(service.notifications().length).toBe(0);
  });

  it('should auto-dismiss after duration', fakeAsync(() => {
    service.show('temp', 'info', 1000);
    expect(service.notifications().length).toBe(1);
    tick(1000);
    expect(service.notifications().length).toBe(0);
  }));

  it('should assign incrementing ids', () => {
    service.show('a', 'info', 99999);
    service.show('b', 'info', 99999);
    const ids = service.notifications().map(n => n.id);
    expect(ids[1]).toBeGreaterThan(ids[0]);
  });
});
