import {ChangeDetectionStrategy, Component, computed, input} from '@angular/core';
import {Marked} from 'marked';

const markdown = new Marked({
  async: false,
  breaks: true,
  gfm: true,
  renderer: {
    html({text}) {
      return escapeHtml(text);
    },
    link({href, title, tokens}) {
      const label = this.parser.parseInline(tokens);
      return renderLink(label, href, title);
    },
    image({href, title, text}) {
      const label = escapeHtml(text || href);
      return renderLink(label, href, title, 'markdown-image-link');
    }
  }
});

@Component({
  selector: 'qd-markdown-content',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="markdown-content" [innerHTML]="renderedContent()"></div>`
})
export class MarkdownContentComponent {
  readonly content = input.required<string>();

  protected readonly renderedContent = computed(() => renderMarkdown(this.content()));
}

export function renderMarkdown(content: string): string {
  return markdown.parse(content, {async: false});
}

function renderLink(label: string, href: string, title: string | null | undefined, className?: string): string {
  const target = safeHref(href);
  if (!target) return label;

  const classAttribute = className ? ` class="${className}"` : '';
  const titleAttribute = title ? ` title="${escapeHtml(title)}"` : '';
  return `<a${classAttribute} href="${target}"${titleAttribute} target="_blank" rel="noopener noreferrer">${label}</a>`;
}

function safeHref(value: string): string | null {
  const href = value.trim();
  if (!href) return null;

  const protocol = href.match(/^([a-z][a-z\d+.-]*):/i)?.[1]?.toLowerCase();
  if (protocol && !['http', 'https', 'mailto', 'tel'].includes(protocol)) return null;

  try {
    return escapeHtml(encodeURI(href).replace(/%25/g, '%'));
  } catch {
    return null;
  }
}

function escapeHtml(value: string): string {
  return value.replace(
    /[&<>"']/g,
    (character) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[character] ?? character)
  );
}
