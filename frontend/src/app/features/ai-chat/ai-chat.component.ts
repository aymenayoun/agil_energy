import { Component, ElementRef, ViewChild, inject, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AIService, ChatHit } from '../../core/services/ai.service';
import { NotificationService } from '../../core/services/notification.service';

interface Message {
  role: 'user' | 'assistant';
  content: string;
  mode?: string;
  model?: string;
  context?: ChatHit[];
  compareOutput?: { default: string; experimental: string };
}

@Component({
  selector: 'app-ai-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ai-chat.component.html',
  styleUrls: ['./ai-chat.component.scss']
})
export class AIChatComponent implements AfterViewChecked {
  @ViewChild('messagesEnd') messagesEnd!: ElementRef;

  private aiService = inject(AIService);
  private notif = inject(NotificationService);

  messages: Message[] = [
    {
      role: 'assistant',
      content: 'Bonjour ! Je suis l\'assistant AGIL Energy. Posez-moi une question sur les prévisions, anomalies, ou sur le contexte tunisien (Ramadan, fériés, prix, météo).'
    }
  ];

  input = '';
  loading = false;
  mode: 'default' | 'experimental' | 'compare' = 'default';
  showContext = false;

  send(): void {
    const question = this.input.trim();
    if (!question || this.loading) return;

    this.messages.push({ role: 'user', content: question });
    this.input = '';
    this.loading = true;

    this.aiService.chat(question, this.mode).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          const d = res.data;
          if (this.mode === 'compare' && typeof d.answer === 'object') {
            this.messages.push({
              role: 'assistant',
              content: '',
              mode: d.mode,
              compareOutput: d.answer as any,
              context: d.retrieved_context
            });
          } else {
            this.messages.push({
              role: 'assistant',
              content: d.answer as string,
              mode: d.mode,
              model: d.model_used,
              context: d.retrieved_context
            });
          }
        } else {
          this.notif.error('Erreur IA');
        }
        this.loading = false;
      },
      error: (err) => {
        this.messages.push({
          role: 'assistant',
          content: `Erreur: ${err?.error?.message || err.message}`
        });
        this.loading = false;
      }
    });
  }

  onKey(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      this.send();
    }
  }

  clear(): void {
    this.messages = this.messages.slice(0, 1);
  }

  rebuildIndex(): void {
    this.loading = true;
    this.aiService.rebuildIndex().subscribe({
      next: () => {
        this.notif.success?.('Index RAG reconstruit');
        this.loading = false;
      },
      error: () => {
        this.notif.error('Échec reconstruction');
        this.loading = false;
      }
    });
  }

  ngAfterViewChecked(): void {
    if (this.messagesEnd) {
      this.messagesEnd.nativeElement.scrollIntoView({ behavior: 'smooth' });
    }
  }
}
