import { useId } from 'react';

/** The Simple Bank mark: a rounded seal with a bank icon, optionally with the wordmark below it. */
export default function BrandMark({ showWordmark = false, size = 160 }) {
  const gradientId = useId();
  return (
    <div className="brand-mark">
      <svg viewBox="0 0 120 120" width={size} height={size} role="img" aria-hidden="true">
        <defs>
          <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stopColor="var(--harbor)" />
            <stop offset="1" stopColor="var(--harbor-dark)" />
          </linearGradient>
        </defs>
        <circle cx="60" cy="60" r="57" fill={`url(#${gradientId})`} stroke="var(--brass)" strokeWidth="2" />
        <path d="M60 28 94 48H26Z" fill="var(--brass)" />
        <rect x="31" y="52" width="8" height="32" rx="2" fill="var(--harbor-tint)" />
        <rect x="56" y="52" width="8" height="32" rx="2" fill="var(--harbor-tint)" />
        <rect x="81" y="52" width="8" height="32" rx="2" fill="var(--harbor-tint)" />
        <rect x="24" y="88" width="72" height="7" rx="2" fill="var(--brass)" />
      </svg>
      {showWordmark && <p className="brand-wordmark">Simple Bank</p>}
    </div>
  );
}
