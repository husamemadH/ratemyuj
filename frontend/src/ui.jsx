/* Shared theme tokens and tiny presentational components. */

export const C = {
  pine: "#0C3B2E",   // الترويسة والأزرار الأساسية
  meadow: "#1E7A5F", // اللمسات وحالات القبول
  chalk: "#F5F4EF",  // خلفية الصفحة
  ink: "#1A211D",    // النص
  amber: "#D9A62E",  // النجوم
  brick: "#B2402F",  // حالات الرفض
  mist: "#DDE3DD",   // الخطوط الفاصلة
};

export const display = { fontFamily: "'Noto Kufi Arabic', 'IBM Plex Sans Arabic', sans-serif" };
export const body = { fontFamily: "'IBM Plex Sans Arabic', 'Noto Kufi Arabic', system-ui, sans-serif" };

/* صيغة الجمع العربية لكلمة تقييم */
export const reviewsWord = (n) => {
  if (n === 0) return "بدون تقييمات";
  if (n === 1) return "تقييم واحد";
  if (n === 2) return "تقييمان";
  if (n <= 10) return `${n} تقييمات`;
  return `${n} تقييم`;
};

export function Stars({ value, size = 16, onSet, hover, onHover }) {
  const active = hover || value;
  return (
    <div className="flex gap-0.5" onMouseLeave={() => onHover && onHover(0)}>
      {[1, 2, 3, 4, 5].map((i) => (
        <svg
          key={i}
          width={size}
          height={size}
          viewBox="0 0 24 24"
          onClick={() => onSet && onSet(i)}
          onMouseEnter={() => onHover && onHover(i)}
          className={onSet ? "cursor-pointer" : ""}
          fill={i <= active ? C.amber : "none"}
          stroke={i <= active ? C.amber : C.mist}
          strokeWidth="2"
        >
          <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
        </svg>
      ))}
    </div>
  );
}

export const GRADES = [
  ["A", "A"], ["A_MINUS", "A−"], ["B_PLUS", "B+"], ["B", "B"], ["B_MINUS", "B−"],
  ["C_PLUS", "C+"], ["C", "C"], ["C_MINUS", "C−"], ["D_PLUS", "D+"], ["D", "D"],
  ["F", "F"], ["WITHDRAWN", "W"], ["IN_PROGRESS", "…"],
];

export const gradeLabel = (g) => (GRADES.find(([k]) => k === g) || ["", g])[1];
