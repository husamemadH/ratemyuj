import { useEffect, useState } from "react";
import { apiGet, apiSend } from "./api";
import { C, display, Stars, gradeLabel } from "./ui";

const STATUS_LABELS = {
  MANUAL_REVIEW: "قيد المراجعة",
  PUBLISHED: "منشورة",
  REJECTED: "مرفوضة",
  HIDDEN: "مخفية",
  REMOVED: "محذوفة",
};

const STATUS_COLORS = {
  MANUAL_REVIEW: C.amber,
  PUBLISHED: C.meadow,
  REJECTED: C.brick,
  HIDDEN: "#8A968D",
  REMOVED: "#5C6A61",
};

const VERDICT_LABELS = {
  APPROVE: "موافقة",
  REJECT: "رفض",
  ESCALATE: "تحويل يدوي",
};

const CATEGORY_LABELS = {
  PROFANITY: "كلمات نابية",
  PERSONAL_ATTACK: "هجوم شخصي",
  DISCRIMINATION: "تمييز",
  UNSUBSTANTIATED_ACCUSATION: "ادعاء غير مؤكد",
  PRIVATE_INFO: "معلومات خاصة",
  NOT_CONSTRUCTIVE: "غير مفيد",
  OFF_TOPIC: "خارج الموضوع",
  MODERATION_UNAVAILABLE: "الفحص الآلي غير متاح",
};

const FILTERS = ["MANUAL_REVIEW", "REJECTED", "PUBLISHED", "HIDDEN", "REMOVED"];

const ACTIONS = {
  MANUAL_REVIEW: [["PUBLISHED", "انشر"], ["REJECTED", "ارفض"]],
  REJECTED: [["PUBLISHED", "انشر"]],
  PUBLISHED: [["HIDDEN", "أخفِ"], ["REMOVED", "احذف"]],
  HIDDEN: [["PUBLISHED", "أعد النشر"], ["REMOVED", "احذف"]],
  REMOVED: [["PUBLISHED", "أعد النشر"]],
};

export default function Admin({ onBack }) {
  const [key, setKey] = useState(() => sessionStorage.getItem("adminKey") || "");
  const [draft, setDraft] = useState("");
  const [status, setStatus] = useState("MANUAL_REVIEW");
  const [page, setPage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [acting, setActing] = useState("");

  const load = async (adminKey, filterStatus) => {
    setLoading(true);
    setError("");
    try {
      const data = await apiGet(`/api/admin/reviews?status=${filterStatus}&size=100`, {
        headers: { "X-Admin-Key": adminKey },
      });
      setPage(data);
    } catch (requestError) {
      if (requestError.status === 403) {
        sessionStorage.removeItem("adminKey");
        setKey("");
        setError("مفتاح الإدارة غير صحيح");
      } else {
        setError(requestError.message);
      }
      setPage(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (key) load(key, status);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, status]);

  const unlock = (event) => {
    event.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    sessionStorage.setItem("adminKey", trimmed);
    setKey(trimmed);
    setDraft("");
  };

  const lock = () => {
    sessionStorage.removeItem("adminKey");
    setKey("");
    setPage(null);
    setError("");
    setNotice("");
  };

  const act = async (review, target) => {
    const label = (ACTIONS[review.status] || []).find(([t]) => t === target)?.[1] || target;
    if ((target === "REJECTED" || target === "HIDDEN" || target === "REMOVED")
        && !window.confirm(`تأكيد: ${label}؟`)) {
      return;
    }
    setActing(review.id);
    setError("");
    setNotice("");
    try {
      const updated = await apiSend(`/api/admin/reviews/${review.id}`, "PATCH", { status: target },
        { headers: { "X-Admin-Key": key } });
      setNotice(`تم تحديث التقييم إلى «${STATUS_LABELS[updated.status] || updated.status}»`);
      await load(key, status);
    } catch (requestError) {
      if (requestError.status === 403) {
        lock();
        setError("مفتاح الإدارة غير صحيح");
      } else {
        setError(requestError.message);
      }
    } finally {
      setActing("");
    }
  };

  if (!key) {
    return (
      <div className="max-w-md mx-auto px-4 py-16">
        <button onClick={onBack} className="text-sm mb-6" style={{ color: C.meadow }}>→ رجوع</button>
        <form onSubmit={unlock} className="rounded-2xl bg-white p-6" style={{ border: `1px solid ${C.mist}` }}>
          <h1 className="text-lg font-semibold mb-1" style={display}>طابور الإدارة</h1>
          <p className="text-xs mb-4 leading-relaxed" style={{ color: "#5C6A61" }}>
            أدخل مفتاح الإدارة للوصول إلى التقييمات المحوّلة للمراجعة اليدوية
            وقرارات الفحص الآلي.
          </p>
          <input
            type="password"
            dir="ltr"
            autoFocus
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="admin key"
            className="w-full rounded-xl px-4 py-3 text-sm outline-none mb-3"
            style={{ border: `1px solid ${C.mist}`, color: C.ink }}
          />
          {error && <p className="text-xs mb-3" style={{ color: C.brick }}>{error}</p>}
          <button type="submit"
            className="w-full px-5 py-2.5 rounded-xl text-sm font-semibold"
            style={{ background: C.pine, color: "white" }}>
            دخول
          </button>
          <p className="text-[11px] mt-3" style={{ color: "#8A968D" }}>
            يُحفظ المفتاح في هذه الجلسة فقط، ولا يُرسل إلا في ترويسة الطلبات.
          </p>
        </form>
      </div>
    );
  }

  const reviews = page?.content || [];

  return (
    <div className="max-w-3xl mx-auto px-4 pb-16">
      <div className="flex items-center justify-between my-4">
        <button onClick={onBack} className="text-sm" style={{ color: C.meadow }}>→ رجوع للبحث</button>
        <button onClick={lock} className="text-xs px-3 py-1.5 rounded-full" style={{ border: `1px solid ${C.mist}` }}>
          قفل
        </button>
      </div>

      <div className="rounded-2xl bg-white p-5 mb-4" style={{ border: `1px solid ${C.mist}` }}>
        <h1 className="text-xl font-bold mb-1" style={display}>طابور الإدارة</h1>
        <p className="text-xs" style={{ color: "#5C6A61" }}>
          {page ? `${page.totalElements} تقييم في هذه الحالة` : "…"}
        </p>
      </div>

      <div className="flex flex-wrap gap-2 mb-4">
        {FILTERS.map((filter) => (
          <button key={filter} onClick={() => setStatus(filter)}
            className="px-3 py-1.5 rounded-full text-xs"
            style={status === filter
              ? { background: C.pine, color: "white" }
              : { border: `1px solid ${C.mist}`, color: C.ink }}>
            {STATUS_LABELS[filter]}
          </button>
        ))}
      </div>

      {notice && <p className="text-xs mb-3" style={{ color: C.meadow }}>{notice}</p>}
      {error && <p className="text-xs mb-3" style={{ color: C.brick }}>{error}</p>}

      {loading && <p className="text-center py-10 text-sm" style={{ color: "#5C6A61" }}>لحظات…</p>}

      {!loading && reviews.length === 0 && (
        <div className="rounded-2xl p-10 text-center" style={{ border: `1px dashed ${C.mist}` }}>
          <p className="text-sm" style={{ color: "#5C6A61" }}>لا يوجد تقييمات بهذه الحالة.</p>
        </div>
      )}

      <div className="space-y-3">
        {reviews.map((review) => (
          <div key={review.id} className="rounded-2xl bg-white p-5" style={{ border: `1px solid ${C.mist}` }}>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2 mb-2">
              <Stars value={review.rating} size={15} />
              <span className="px-2 py-0.5 rounded text-xs font-medium"
                style={{ background: C.chalk, border: `1px solid ${C.mist}` }} dir="ltr">
                {review.courseCode}
              </span>
              <span className="text-xs font-medium">{review.professorName}</span>
              <span className="text-xs px-2 py-0.5 rounded-full text-white"
                style={{ background: STATUS_COLORS[review.status] || C.pine }}>
                {STATUS_LABELS[review.status] || review.status}
              </span>
              <span className="text-xs ms-auto" style={{ color: "#B0BAB2" }} dir="ltr">
                {String(review.updatedAt || "").slice(0, 16).replace("T", " ")}
              </span>
            </div>

            <p className="text-sm leading-relaxed mb-3">{review.comment}</p>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 text-xs mb-3" style={{ color: "#5C6A61" }}>
              <span>العلامة: <span dir="ltr">{gradeLabel(review.grade)}</span></span>
              <span>الصعوبة: {review.difficulty ?? "—"}</span>
              <span>يعيد المادة: {review.wouldTakeAgain == null ? "—" : review.wouldTakeAgain ? "نعم" : "لا"}</span>
              <span className="truncate" dir="ltr" title={review.studentHash}>
                #{String(review.studentHash || "").slice(0, 12)}
              </span>
            </div>

            {review.moderation && (
              <div className="rounded-xl p-3 mb-3 text-xs" style={{ background: C.chalk }}>
                <div className="flex flex-wrap gap-x-3 gap-y-1 mb-1">
                  <span>قرار الفحص: <b>{VERDICT_LABELS[review.moderation.verdict] || review.moderation.verdict}</b></span>
                  <span>الثقة: {Math.round((review.moderation.confidence || 0) * 100)}%</span>
                  <span style={{ color: "#8A968D" }} dir="ltr">{review.moderation.model}</span>
                </div>
                {review.moderation.flaggedCategories?.length > 0 && (
                  <div className="flex flex-wrap gap-1 mb-1">
                    {review.moderation.flaggedCategories.map((category) => (
                      <span key={category} className="px-2 py-0.5 rounded-full"
                        style={{ background: "#F0E1CE", color: "#7A4A16" }}>
                        {CATEGORY_LABELS[category] || category}
                      </span>
                    ))}
                  </div>
                )}
                {review.moderation.internalReason && (
                  <p dir="ltr" className="text-[11px] leading-relaxed" style={{ color: "#5C6A61" }}>
                    {review.moderation.internalReason}
                  </p>
                )}
              </div>
            )}

            <div className="flex flex-wrap gap-2">
              {(ACTIONS[review.status] || []).map(([target, label]) => (
                <button key={target} disabled={acting === review.id} onClick={() => act(review, target)}
                  className="px-4 py-1.5 rounded-lg text-xs font-medium disabled:opacity-50"
                  style={target === "PUBLISHED"
                    ? { background: C.pine, color: "white" }
                    : { border: `1px solid ${C.brick}`, color: C.brick }}>
                  {label}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
