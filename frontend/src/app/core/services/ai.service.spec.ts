import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AIService } from './ai.service';
import { environment } from '../../../environments/environment';

describe('AIService', () => {
  let service: AIService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(AIService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('chat() should POST to /ai/chat', () => {
    service.chat('What is AGIL?', 'default').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/ai/chat`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ question: 'What is AGIL?', mode: 'default' });
    req.flush({ success: true, data: { question: 'What is AGIL?', answer: 'test', mode: 'default' } });
  });

  it('explain() should POST to /ai/explain', () => {
    service.explain(1, 2, 'experimental').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/ai/explain`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ stationId: 1, fuelTypeId: 2, mode: 'experimental' });
    req.flush({ success: true, data: {} });
  });

  it('rebuildIndex() should POST to /ai/rag/rebuild', () => {
    service.rebuildIndex().subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/ai/rag/rebuild`);
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, data: {} });
  });
});
