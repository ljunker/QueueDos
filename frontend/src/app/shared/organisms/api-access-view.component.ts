import {ChangeDetectionStrategy, Component, OnInit, inject, signal} from '@angular/core';
import {finalize} from 'rxjs';

import {ApiClientService} from '../../core/api-client.service';
import {CreatedMcpAccessToken, McpAccessToken} from '../../core/api.models';

@Component({
  selector: 'qd-api-access-view',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="api-access-view">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Integrations</p>
          <h2>API access</h2>
          <p class="muted">Connect an MCP-compatible assistant with your personal QueueDos permissions.</p>
        </div>
      </div>

      <section class="panel api-access-connect">
        <h3>MCP connection</h3>
        <p class="muted">Use Streamable HTTP and send your personal token as a bearer token.</p>
        <dl class="api-access-details">
          <div><dt>URL</dt><dd><code>{{ mcpUrl }}</code></dd></div>
          <div><dt>Header</dt><dd><code>Authorization: Bearer &lt;token&gt;</code></dd></div>
        </dl>
      </section>

      @if (createdToken(); as created) {
        <section class="panel api-token-secret" aria-live="polite">
          <div class="section-heading compact">
            <div>
              <h3>Copy your new token now</h3>
              <p class="muted">QueueDos will not show this secret again.</p>
            </div>
            <button type="button" class="ghost" (click)="dismissSecret()">Done</button>
          </div>
          <div class="api-token-secret-value">
            <code>{{ created.token }}</code>
            <button type="button" (click)="copyToken(created)">Copy token</button>
          </div>
        </section>
      }

      <section class="panel">
        <div class="section-heading compact">
          <div>
            <h3>Personal tokens</h3>
            <p class="muted">Tokens expire automatically after 90 days and use your current project roles.</p>
          </div>
        </div>

        <form class="api-token-create" (submit)="createToken($event)">
          <label>
            <span>Token name</span>
            <input
              type="text"
              maxlength="160"
              autocomplete="off"
              placeholder="Claude on my laptop"
              [value]="tokenName()"
              (input)="tokenName.set(valueOf($event))">
          </label>
          <button type="submit" [disabled]="busy() || !tokenName().trim()">Create token</button>
        </form>

        @if (error()) {
          <p class="error">{{ error() }}</p>
        }

        @if (loading()) {
          <p class="muted">Loading tokens...</p>
        } @else if (tokens().length === 0) {
          <p class="muted">No active personal tokens.</p>
        } @else {
          <div class="api-token-list">
            @for (token of tokens(); track token.id) {
              <article class="api-token-row">
                <div>
                  <strong>{{ token.name }}</strong>
                  <code>{{ token.tokenHint }}</code>
                  <small>Created {{ formatted(token.createdAt) }} · Expires {{ formatted(token.expiresAt) }}</small>
                  <small>Last used {{ token.lastUsedAt ? formatted(token.lastUsedAt) : 'never' }}</small>
                </div>
                <button type="button" class="danger" [disabled]="busy()" (click)="revokeToken(token)">Revoke</button>
              </article>
            }
          </div>
        }
      </section>
    </section>
  `
})
export class ApiAccessViewComponent implements OnInit {
  private readonly api = inject(ApiClientService);

  readonly mcpUrl = `${window.location.origin}/mcp`;
  readonly tokens = signal<McpAccessToken[]>([]);
  readonly createdToken = signal<CreatedMcpAccessToken | null>(null);
  readonly tokenName = signal('');
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');

  ngOnInit(): void {
    this.loadTokens();
  }

  createToken(event: Event): void {
    event.preventDefault();
    const name = this.tokenName().trim();
    if (!name || this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.api.createMcpToken({name}).pipe(finalize(() => this.busy.set(false))).subscribe({
      next: (created) => {
        this.createdToken.set(created);
        this.tokens.update((tokens) => [created.accessToken, ...tokens]);
        this.tokenName.set('');
      },
      error: () => this.error.set('The token could not be created.')
    });
  }

  revokeToken(token: McpAccessToken): void {
    if (this.busy() || !window.confirm(`Revoke the token "${token.name}"?`)) return;
    this.busy.set(true);
    this.error.set('');
    this.api.revokeMcpToken(token.id).pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.tokens.update((tokens) => tokens.filter((candidate) => candidate.id !== token.id));
        if (this.createdToken()?.accessToken.id === token.id) this.createdToken.set(null);
      },
      error: () => this.error.set('The token could not be revoked.')
    });
  }

  copyToken(created: CreatedMcpAccessToken): void {
    void navigator.clipboard.writeText(created.token).catch(() => {
      this.error.set('Copy failed. Select and copy the token manually.');
    });
  }

  dismissSecret(): void {
    this.createdToken.set(null);
  }

  valueOf(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  formatted(value: string): string {
    return new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short'}).format(new Date(value));
  }

  private loadTokens(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.mcpTokens().pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (tokens) => this.tokens.set(tokens),
      error: () => this.error.set('Personal tokens could not be loaded.')
    });
  }
}
