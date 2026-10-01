import { useId } from 'react';

const ICONS = {
  account: (
    <>
      <rect x="32" y="38" width="56" height="46" rx="6" fill="var(--harbor-tint)" />
      <rect x="32" y="38" width="56" height="13" rx="6" fill="var(--brass)" />
      <line x1="60" y1="58" x2="60" y2="76" stroke="var(--harbor-dark)" strokeWidth="6" strokeLinecap="round" />
      <line x1="51" y1="67" x2="69" y2="67" stroke="var(--harbor-dark)" strokeWidth="6" strokeLinecap="round" />
    </>
  ),
  transfer: (
    <>
      <path d="M33 50h42l-11-11" fill="none" stroke="var(--harbor-tint)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M87 71H45l11 11" fill="none" stroke="var(--harbor-tint)" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" />
    </>
  ),
  profile: (
    <>
      <circle cx="60" cy="47" r="14" fill="var(--harbor-tint)" />
      <path d="M33 87c4-17 19-25 27-25s23 8 27 25" fill="none" stroke="var(--harbor-tint)" strokeWidth="7" strokeLinecap="round" />
    </>
  ),
};

/** A circular icon badge for a specific feature (open account, transfer, profile), matching BrandMark's style. */
export default function FeatureIcon({ kind, size = 120 }) {
  const gradientId = useId();
  return (
    <svg viewBox="0 0 120 120" width={size} height={size} role="img" aria-hidden="true">
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--harbor)" />
          <stop offset="1" stopColor="var(--harbor-dark)" />
        </linearGradient>
      </defs>
      <circle cx="60" cy="60" r="57" fill={`url(#${gradientId})`} stroke="var(--brass)" strokeWidth="2" />
      {ICONS[kind]}
    </svg>
  );
}
