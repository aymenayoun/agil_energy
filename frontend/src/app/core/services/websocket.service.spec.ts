import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';
import { NotificationService } from './notification.service';

describe('WebSocketService', () => {
  let service: WebSocketService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [NotificationService]
    });
    service = TestBed.inject(WebSocketService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with connected = false', () => {
    expect(service.connected()).toBeFalse();
  });

  it('should start with null latestAlert', () => {
    expect(service.latestAlert()).toBeNull();
  });

  it('should start with empty alertFeed', () => {
    expect(service.alertFeed()).toEqual([]);
  });

  it('should start with unreadCount = 0', () => {
    expect(service.unreadCount()).toBe(0);
  });

  it('resetUnread() should set unreadCount to 0', () => {
    service.unreadCount.set(5);
    service.resetUnread();
    expect(service.unreadCount()).toBe(0);
  });

  it('connect() should skip when no token in localStorage', () => {
    spyOn(localStorage, 'getItem').and.returnValue(null);
    service.connect();
    expect(service.connected()).toBeFalse();
  });

  it('disconnect() should set connected to false', () => {
    service.disconnect();
    expect(service.connected()).toBeFalse();
  });
});
