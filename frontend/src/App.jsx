import { useState, useEffect, useRef } from "react";
import { apiGet, apiSend } from "./api";
import { C, display, body, reviewsWord, Stars, GRADES, gradeLabel } from "./ui";
import Admin from "./Admin";

/* ============================================================
   قيّم دكتورك — RateMyUjProfessor (النسخة العربية RTL)
   متصل بالباك إند: Spring Boot + PostgreSQL + فحص Jev.
   ============================================================ */

const PAGE_SIZE = 10;

/* ---------------- لوحة فحص الجودة ----------------
   تظهر أثناء POST /api/reviews ثم تعرض النتيجة. */

const CHECKS = [
  { key: "respect", label: "لغة محترمة" },
  { key: "fair", label: "منصف وواقعي" },
  { key: "useful", label: "مفيد لباقي الطلاب" },
];

function failIndex(result) {
  const cat = (result.flaggedCategories || [])[0];
  if (cat === "PROFANITY" || cat === "PERSONAL_ATTACK" || cat === "DISCRIMINATION") return 0;
  if (cat === "UNSUBSTANTIATED_ACCUSATION" || cat === "PRIVATE_INFO") return 1;
  return 2;
}

function ModerationPanel({ phase, result, onEdit, onDone }) {
  const [step, setStep] = useState(0);
  useEffect(() => {
    if (phase !== "checking") return;
    setStep(0);
    const t1 = setTimeout(() => setStep(1), 800);
    const t2 = setTimeout(() => setStep(2), 1600);
    return () => { clearTimeout(t1); clearTimeout(t2); };
  }, [phase]);

  const done = phase === "done";
  const rejected = done && result.outcome === "REJECTED";
  const held = done && result.outcome === "MANUAL_REVIEW";

  return (
    <div className="rounded-2xl p-5" style={{ background: C.pine, color: "#EDF3EE" }}>
      <div className="flex items-center gap-2 mb-4">
        <span className="inline-block w-2 h-2 rounded-full"
          style={{ background: done ? (rejected ? C.brick : C.amber) : C.amber, animation: done ? "none" : "pulse 1s infinite" }} />
        <p className="text-xs font-medium" style={display}>
          {done ? "اكتمل فحص التقييم" : "نفحص تقييمك الآن…"}
        </p>
      </div>

      <div className="space-y-3">
        {CHECKS.map((c, i) => {
          const state = done
            ? (rejected && result.flaggedCategories.length && i === failIndex(result) ? "fail" : "pass")
            : i < step ? "pass" : i === step ? "active" : "wait";
          return (
            <div key={c.key} className="flex items-center gap-3">
              <span className="w-5 h-5 rounded-full flex items-center justify-center text-xs shrink-0"
                style={{
                  background: state === "pass" ? C.meadow : state === "fail" ? C.brick : "rgba(255,255,255,0.12)",
                  transition: "background 0.4s",
                }}>
                {state === "pass" ? "✓" : state === "fail" ? "✕" : state === "active" ? "·" : ""}
              </span>
              <span className="text-sm" style={{ opacity: state === "wait" ? 0.45 : 1, transition: "opacity 0.4s" }}>
                {c.label}
              </span>
            </div>
          );
        })}
      </div>

      {done && (
        <div className="mt-5 pt-4" style={{ borderTop: "1px solid rgba(255,255,255,0.15)" }}>
          {rejected ? (
            <>
              <p className="font-semibold mb-1" style={{ ...display, color: "#F3C9C0" }}>لم يُنشر بعد</p>
              <p className="text-sm mb-4 leading-relaxed" style={{ opacity: 0.9 }}>{result.feedback}</p>
              <button onClick={onEdit}
                className="px-4 py-2 rounded-lg text-sm font-medium"
                style={{ background: "#EDF3EE", color: C.pine }}>
                عدّل تقييمك
              </button>
            </>
          ) : held ? (
            <>
              <p className="font-semibold mb-1" style={display}>قيد مراجعة سريعة من فريقنا</p>
              <p className="text-sm mb-4" style={{ opacity: 0.9 }}>{result.feedback}</p>
              <button onClick={onDone} className="px-4 py-2 rounded-lg text-sm font-medium"
                style={{ background: "#EDF3EE", color: C.pine }}>تمام</button>
            </>
          ) : (
            <>
              <p className="font-semibold mb-1" style={{ ...display, color: "#CFE8D8" }}>تم النشر — شكراً لك</p>
              <p className="text-sm mb-4" style={{ opacity: 0.9 }}>
                تقييمك صار ظاهر للجميع وانحسب على معدّل الدكتور مباشرة.
              </p>
              <button onClick={onDone} className="px-4 py-2 rounded-lg text-sm font-medium"
                style={{ background: "#EDF3EE", color: C.pine }}>
                اعرضه على الملف
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}

/* ---------------- نموذج التقييم ---------------- */

function ReviewForm({ professor, onRequireAuth, onPublished, onClose }) {
  const [courseId, setCourseId] = useState("");
  const [grade, setGrade] = useState("");
  const [rating, setRating] = useState(0);
  const [hover, setHover] = useState(0);
  const [difficulty, setDifficulty] = useState(0);
  const [wta, setWta] = useState(null);
  const [comment, setComment] = useState("");
  const [phase, setPhase] = useState("form"); // form | checking | done
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const panelRef = useRef(null);

  const ready = courseId && grade && rating > 0 && comment.trim().length >= 20;

  const submit = async () => {
    setError("");
    setPhase("checking");
    setTimeout(() => panelRef.current?.scrollIntoView({ behavior: "smooth", block: "center" }), 50);
    try {
      const body = await apiSend("/api/reviews", "POST", {
        professorId: professor.id,
        courseId,
        rating,
        comment: comment.trim(),
        grade,
        difficulty: difficulty || null,
        wouldTakeAgain: wta,
      });
      setResult(body);
      setPhase("done");
    } catch (requestError) {
      if (requestError.status === 401) onRequireAuth();
      setError(requestError.message);
      setPhase("form");
    }
  };

  const finish = () => {
    if (result.outcome === "PUBLISHED") onPublished();
    onClose();
  };

  return (
    <div className="rounded-2xl bg-white p-5 md:p-6" style={{ border: `1px solid ${C.mist}` }}>
      <div className="flex items-start justify-between mb-5">
        <div>
          <h3 className="text-lg font-semibold" style={display}>قيّم {professor.title} {professor.fullName}</h3>
          <p className="text-xs mt-1" style={{ color: "#5C6A61" }}>
            مجهول الهوية للجميع · تقييم واحد لكل مادة · يُفحص آلياً قبل النشر
          </p>
        </div>
        <button onClick={onClose} className="text-sm px-2 py-1 rounded hover:bg-gray-100">✕</button>
      </div>

      {phase === "form" && (
        <div className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
                المادة — من ملف الدكتور
              </label>
              <div className="flex flex-wrap gap-2">
                {professor.courses.map((course) => (
                  <button key={course.id} onClick={() => setCourseId(course.id)}
                    className="px-3 py-1.5 rounded-full text-sm"
                    style={courseId === course.id
                      ? { background: C.pine, color: "white" }
                      : { border: `1px solid ${C.mist}`, color: C.ink }}>
                    <span dir="ltr">{course.code}</span>
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
                علامتك بالمادة
              </label>
              <div className="flex flex-wrap gap-1.5">
                {GRADES.map(([k, label]) => (
                  <button key={k} onClick={() => setGrade(k)}
                    className="w-9 h-8 rounded text-sm"
                    style={grade === k
                      ? { background: C.meadow, color: "white" }
                      : { border: `1px solid ${C.mist}` }}>
                    <span dir="ltr">{label}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
                التقييم العام
              </label>
              <Stars value={rating} hover={hover} onHover={setHover} onSet={setRating} size={26} />
            </div>
            <div>
              <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
                الصعوبة (اختياري)
              </label>
              <div className="flex gap-1.5">
                {[1, 2, 3, 4, 5].map((d) => (
                  <button key={d} onClick={() => setDifficulty(d === difficulty ? 0 : d)}
                    className="w-8 h-8 rounded text-sm"
                    style={difficulty === d
                      ? { background: C.ink, color: "white" }
                      : { border: `1px solid ${C.mist}` }}>
                    {d}
                  </button>
                ))}
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
                بتاخذ عنده مرة ثانية؟
              </label>
              <div className="flex gap-2">
                {[["نعم", true], ["لا", false]].map(([label, v]) => (
                  <button key={label} onClick={() => setWta(wta === v ? null : v)}
                    className="px-4 h-8 rounded-full text-sm"
                    style={wta === v
                      ? { background: v ? C.meadow : C.brick, color: "white" }
                      : { border: `1px solid ${C.mist}` }}>
                    {label}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
              تقييمك — بالعربي، English، أو عربيزي
            </label>
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              rows={4}
              maxLength={1000}
              placeholder="شو لازم يعرف الطالب اللي بعدك؟ المحاضرات، الامتحانات، العلامات، الضغط…"
              className="w-full rounded-xl p-3 text-sm outline-none resize-none"
              style={{ border: `1px solid ${C.mist}`, background: C.chalk }}
            />
            <div className="flex justify-between mt-1 text-xs" style={{ color: "#8A968D" }}>
              <span>خلّي التركيز على التدريس — الإهانات الشخصية ما رح تعدّي الفحص.</span>
              <span dir="ltr">{comment.length}/1000</span>
            </div>
          </div>

          {error && <p className="text-sm" style={{ color: C.brick }}>{error}</p>}

          <button
            disabled={!ready}
            onClick={submit}
            className="w-full md:w-auto px-6 py-2.5 rounded-xl text-sm font-semibold transition-opacity"
            style={{ background: C.pine, color: "white", opacity: ready ? 1 : 0.4 }}>
            أرسل للفحص
          </button>
        </div>
      )}

      {phase !== "form" && (
        <div ref={panelRef}>
          <ModerationPanel
            phase={phase}
            result={result}
            onEdit={() => setPhase("form")}
            onDone={finish}
          />
        </div>
      )}
    </div>
  );
}

/* ---------------- ملف الدكتور ---------------- */

function BreakdownBar({ label, count, total }) {
  const pct = total ? (count / total) * 100 : 0;
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="w-3" style={{ color: "#5C6A61" }}>{label}</span>
      <div className="flex-1 h-2 rounded-full overflow-hidden" style={{ background: C.mist }}>
        <div className="h-full rounded-full" style={{ width: pct + "%", background: C.meadow, transition: "width 0.6s" }} />
      </div>
      <span className="w-6" style={{ color: "#8A968D" }}>{count}</span>
    </div>
  );
}

function Profile({ professorId, onBack, signedIn, authReady, onRequireAuth }) {
  const [professor, setProfessor] = useState(null);
  const [reviews, setReviews] = useState([]);
  const [status, setStatus] = useState("loading"); // loading | ready | error
  const [error, setError] = useState("");
  const [writing, setWriting] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);

  const load = async () => {
    setStatus("loading");
    setError("");
    try {
      const [detail, reviewPage] = await Promise.all([
        apiGet(`/api/professors/${professorId}`),
        apiGet(`/api/professors/${professorId}/reviews?page=0&size=${PAGE_SIZE}`),
      ]);
      setProfessor(detail);
      setReviews(reviewPage.content || []);
      setPage(0);
      setHasMore(!reviewPage.last);
      setStatus("ready");
    } catch (loadError) {
      setError(loadError.message);
      setStatus("error");
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [professorId]);

  useEffect(() => {
    if (!signedIn) setWriting(false);
  }, [signedIn]);

  const loadMore = async () => {
    const next = page + 1;
    try {
      const data = await apiGet(`/api/professors/${professorId}/reviews?page=${next}&size=${PAGE_SIZE}`);
      setReviews((current) => [...current, ...(data.content || [])]);
      setPage(next);
      setHasMore(!data.last);
    } catch (loadError) {
      setError(loadError.message);
    }
  };

  if (status === "loading") {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center text-sm" style={{ color: "#5C6A61" }}>
        لحظات… نحمّل الملف
      </div>
    );
  }

  if (status === "error" || !professor) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-16 text-center">
        <p className="text-sm mb-3" style={{ color: C.brick }}>{error || "تعذّر تحميل الملف"}</p>
        <button onClick={load} className="text-sm px-4 py-2 rounded-lg" style={{ border: `1px solid ${C.mist}` }}>
          حاول مرة ثانية
        </button>
      </div>
    );
  }

  const count = professor.reviewCount;
  const breakdown = professor.breakdown || {};

  return (
    <div className="max-w-3xl mx-auto px-4 pb-16">
      <button onClick={onBack} className="text-sm my-4 flex items-center gap-1" style={{ color: C.meadow }}>
        → رجوع للبحث
      </button>

      <div className="rounded-2xl bg-white p-5 md:p-7 mb-4" style={{ border: `1px solid ${C.mist}` }}>
        <div className="flex flex-col md:flex-row md:items-start gap-6">
          <div className="flex-1">
            <p className="text-xs mb-1 font-medium" style={{ color: C.meadow, ...display }}>
              {professor.department} · {professor.college}
            </p>
            <h1 className="text-2xl md:text-3xl font-bold" style={display}>
              {professor.title} {professor.fullName}
            </h1>
            <div className="flex flex-wrap gap-2 mt-3">
              {professor.courses.map((course) => (
                <span key={course.id} className="px-2.5 py-1 rounded-full text-xs"
                  style={{ background: C.chalk, border: `1px solid ${C.mist}` }}>
                  <span dir="ltr">{course.code}</span> · {course.name}
                </span>
              ))}
            </div>
            <button onClick={() => {
                if (!authReady) return;
                if (signedIn) setWriting(true);
                else onRequireAuth();
              }}
              className="mt-5 px-5 py-2.5 rounded-xl text-sm font-semibold"
              style={{ background: C.pine, color: "white" }}>
              اكتب تقييمك
            </button>
          </div>

          <div className="md:w-64 shrink-0">
            <div className="flex items-end gap-2">
              <span className="text-6xl font-bold leading-none" style={{ ...display, color: C.pine }} dir="ltr">
                {count ? professor.avgRating.toFixed(1) : "—"}
              </span>
              <div className="pb-1.5">
                <Stars value={Math.round(professor.avgRating)} size={14} />
                <p className="text-xs mt-1" style={{ color: "#8A968D" }}>
                  {reviewsWord(count)}
                </p>
              </div>
            </div>
            <div className="mt-3 space-y-1.5">
              {[5, 4, 3, 2, 1].map((s) => (
                <BreakdownBar key={s} label={s} count={breakdown[String(s)] || 0} total={count} />
              ))}
            </div>
          </div>
        </div>
      </div>

      {writing && (
        <div className="mb-4">
          <ReviewForm
            professor={professor}
            onRequireAuth={onRequireAuth}
            onPublished={load}
            onClose={() => setWriting(false)}
          />
        </div>
      )}

      {reviews.length === 0 && !writing ? (
        <div className="rounded-2xl p-10 text-center" style={{ border: `1px dashed ${C.mist}` }}>
          <p className="font-semibold mb-1" style={display}>لا يوجد تقييمات بعد</p>
          <p className="text-sm" style={{ color: "#5C6A61" }}>
            أخذت مادة عند {professor.title} {professor.fullName}؟ كن أول من يساعد باقي الطلاب.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {reviews.map((r) => (
            <div key={r.id} className="rounded-2xl bg-white p-5" style={{ border: `1px solid ${C.mist}` }}>
              <div className="flex flex-wrap items-center gap-x-3 gap-y-2 mb-2">
                <Stars value={r.rating} size={15} />
                <span className="px-2 py-0.5 rounded text-xs font-medium"
                  style={{ background: C.chalk, border: `1px solid ${C.mist}` }} dir="ltr">
                  {r.courseCode}
                </span>
                <span className="text-xs" style={{ color: "#8A968D" }}>
                  العلامة: <span dir="ltr">{gradeLabel(r.grade)}</span>
                </span>
                {r.difficulty && (
                  <span className="text-xs" style={{ color: "#8A968D" }}>الصعوبة {r.difficulty}/5</span>
                )}
                {r.wouldTakeAgain != null && (
                  <span className="text-xs font-medium" style={{ color: r.wouldTakeAgain ? C.meadow : C.brick }}>
                    {r.wouldTakeAgain ? "بياخذ عنده مرة ثانية" : "ما بياخذ عنده مرة ثانية"}
                  </span>
                )}
                {r.mine && (
                  <span className="text-xs px-2 py-0.5 rounded-full" style={{ background: "#E4F0E9", color: C.meadow }}>
                    تقييمك
                  </span>
                )}
                <span className="text-xs ms-auto" style={{ color: "#B0BAB2" }} dir="ltr">
                  {String(r.createdAt || "").slice(0, 10)}
                </span>
              </div>
              <p className="text-sm leading-relaxed">{r.comment}</p>
            </div>
          ))}
          {hasMore && (
            <button onClick={loadMore}
              className="w-full py-3 rounded-2xl text-sm font-medium"
              style={{ border: `1px solid ${C.mist}`, color: C.meadow }}>
              عرض المزيد
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/* ---------------- الصفحة الرئيسية / البحث ---------------- */

function Home({ onOpen }) {
  const [q, setQ] = useState("");
  const [results, setResults] = useState([]);
  const [status, setStatus] = useState("loading"); // loading | ready | error

  useEffect(() => {
    let cancelled = false;
    setStatus("loading");
    const timer = setTimeout(() => {
      apiGet(`/api/professors?q=${encodeURIComponent(q.trim())}&size=30`)
        .then((data) => {
          if (cancelled) return;
          setResults(data.content || []);
          setStatus("ready");
        })
        .catch(() => {
          if (cancelled) return;
          setResults([]);
          setStatus("error");
        });
    }, 250);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [q]);

  return (
    <div>
      <div style={{ background: C.pine }} className="px-4 pt-12 pb-16 text-center">
        <p className="text-sm mb-2 font-medium" style={{ color: "#9DC3AE", ...display }}>
          الجامعة الأردنية · من الطلاب للطلاب
        </p>
        <h1 className="text-3xl md:text-5xl font-bold text-white mb-6 leading-snug" style={display}>
          اعرف المادة<br />قبل ما تسجّلها.
        </h1>
        <div className="max-w-xl mx-auto relative">
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="ابحث باسم الدكتور أو القسم أو رمز المادة…"
            className="w-full rounded-2xl px-5 py-4 text-sm outline-none shadow-lg"
            style={{ background: "white", color: C.ink }}
          />
        </div>
        <p className="text-xs mt-4" style={{ color: "#7FA890" }}>
          لطلبة الأردنية الموثّقين فقط · التقييمات مجهولة الهوية · كل تعليق يُفحص آلياً
        </p>
      </div>

      <div className="max-w-2xl mx-auto px-4 -mt-6 pb-16 space-y-3">
        {results.map((p) => (
          <button key={p.id} onClick={() => onOpen(p.id)}
            className="w-full text-start rounded-2xl bg-white p-5 flex items-center gap-4 hover:shadow-md transition-shadow"
            style={{ border: `1px solid ${C.mist}` }}>
            <div className="w-14 text-center shrink-0">
              <span className="text-2xl font-bold block" style={{ ...display, color: p.reviewCount ? C.pine : "#B0BAB2" }} dir="ltr">
                {p.reviewCount ? p.avgRating.toFixed(1) : "—"}
              </span>
              <span className="text-xs" style={{ color: "#8A968D" }}>
                {reviewsWord(p.reviewCount)}
              </span>
            </div>
            <div className="min-w-0">
              <p className="font-semibold truncate" style={display}>{p.title} {p.fullName}</p>
              <p className="text-xs truncate" style={{ color: "#5C6A61" }}>
                {p.department}
                {p.courseCodes?.length ? <> · <span dir="ltr">{p.courseCodes.join(", ")}</span></> : null}
              </p>
            </div>
            <span className="ms-auto" style={{ color: C.meadow }}>←</span>
          </button>
        ))}
        {status === "loading" && results.length === 0 && (
          <div className="text-center py-10 text-sm" style={{ color: "#5C6A61" }}>لحظات…</div>
        )}
        {status === "error" && (
          <div className="text-center py-10 text-sm" style={{ color: C.brick }}>
            تعذّر الاتصال بالخادم. تأكد إن الباك إند شغّال.
          </div>
        )}
        {status === "ready" && results.length === 0 && (
          <div className="text-center py-10 text-sm" style={{ color: "#5C6A61" }}>
            لا يوجد دكتور يطابق «{q}». جرّب اسماً آخر أو ابحث برمز المادة.
          </div>
        )}
      </div>
    </div>
  );
}

/* ---------------- الدخول ---------------- */

function LoginPanel({ onClose, onSuccess }) {
  const [step, setStep] = useState("email");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const failMessage = async (res) => {
    const body = await res.json().catch(() => ({}));
    return body.error || "تعذّر إكمال الطلب";
  };

  const send = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const res = await fetch("/api/auth/request", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      if (!res.ok) {
        setError(await failMessage(res));
        return;
      }
      setStep("code");
    } catch {
      setError("تعذّر الاتصال بالخادم");
    } finally {
      setBusy(false);
    }
  };

  const confirm = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const res = await fetch("/api/auth/verify", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, code }),
      });
      if (!res.ok) {
        setError(await failMessage(res));
        return;
      }
      onSuccess();
    } catch {
      setError("تعذّر الاتصال بالخادم");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-20 flex items-end md:items-center justify-center px-4 py-6"
      style={{ background: "rgba(12,59,46,0.45)" }}
      onClick={onClose}>
      <form onSubmit={step === "email" ? send : confirm}
        className="w-full max-w-md rounded-2xl bg-white p-5 md:p-6"
        style={{ border: `1px solid ${C.mist}` }}
        onClick={(event) => event.stopPropagation()}>
        <div className="flex items-start justify-between mb-4">
          <div>
            <h2 className="text-lg font-semibold" style={display}>دخول ببريد الجامعة</h2>
            <p className="text-xs mt-1" style={{ color: "#5C6A61" }}>
              رمز لمرة واحدة على بريد ju.edu.jo. ما منخزّن البريد مع التقييم.
            </p>
          </div>
          <button type="button" onClick={onClose} className="text-sm px-2 py-1 rounded hover:bg-gray-100">✕</button>
        </div>

        {step === "email" ? (
          <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
            البريد الجامعي
            <input
              type="email"
              dir="ltr"
              autoFocus
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="name@ju.edu.jo"
              className="mt-2 w-full rounded-xl px-4 py-3 text-sm outline-none"
              style={{ border: `1px solid ${C.mist}`, color: C.ink }}
            />
          </label>
        ) : (
          <label className="block text-xs font-medium mb-2" style={{ color: "#5C6A61" }}>
            الرمز المرسل إلى <span dir="ltr">{email.trim()}</span>
            <input
              inputMode="numeric"
              autoFocus
              dir="ltr"
              maxLength={6}
              value={code}
              onChange={(event) => setCode(event.target.value)}
              placeholder="000000"
              className="mt-2 w-full rounded-xl px-4 py-3 text-sm outline-none tracking-[0.4em] text-center"
              style={{ border: `1px solid ${C.mist}`, color: C.ink }}
            />
          </label>
        )}

        {error && (
          <p className="text-xs mt-3" style={{ color: C.brick }}>{error}</p>
        )}

        <div className="flex items-center justify-between gap-3 mt-5">
          <button type="submit" disabled={busy}
            className="px-5 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-60"
            style={{ background: C.pine, color: "white" }}>
            {busy ? "لحظات…" : step === "email" ? "أرسل الرمز" : "تأكيد"}
          </button>
          {step === "code" ? (
            <button type="button" onClick={() => { setStep("email"); setError(""); }}
              className="text-xs" style={{ color: C.meadow }}>
              تغيير البريد
            </button>
          ) : <span />}
        </div>
      </form>
    </div>
  );
}

/* ---------------- هيكل التطبيق ---------------- */

export default function App() {
  const isAdminHash = () => window.location.hash === "#/admin";
  const [view, setView] = useState(() => ({ page: isAdminHash() ? "admin" : "home", profId: null }));
  const [auth, setAuth] = useState("unknown");
  const [showLogin, setShowLogin] = useState(false);

  useEffect(() => {
    const onHash = () => {
      setView((current) => isAdminHash()
        ? { page: "admin", profId: null }
        : current.page === "admin"
          ? { page: "home", profId: null }
          : current);
    };
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);

  const openAdmin = () => {
    window.location.hash = "#/admin";
    setView({ page: "admin", profId: null });
  };

  const goHome = () => {
    if (isAdminHash()) window.location.hash = "";
    setView({ page: "home", profId: null });
  };

  useEffect(() => {
    let cancelled = false;
    fetch("/api/auth/me", { credentials: "include" })
      .then((res) => (res.ok ? res.json() : { authenticated: false }))
      .then((body) => {
        if (!cancelled) setAuth(body.authenticated ? "authenticated" : "anonymous");
      })
      .catch(() => {
        if (!cancelled) setAuth("anonymous");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const logout = async () => {
    try {
      await fetch("/api/auth/logout", { method: "POST", credentials: "include" });
    } catch {
      /* a failed call leaves the cookie until it expires */
    }
    setAuth("anonymous");
    setShowLogin(false);
  };

  return (
    <div dir="rtl" lang="ar" className="min-h-screen" style={{ background: C.chalk, color: C.ink, ...body }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans+Arabic:wght@400;500;700&family=Noto+Kufi+Arabic:wght@500;700&display=swap');
        @keyframes pulse { 0%,100% { opacity: 1 } 50% { opacity: 0.3 } }
        * { -webkit-tap-highlight-color: transparent; }
      `}</style>

      <header className="px-4 py-3 flex items-center justify-between"
        style={{ background: C.pine, borderBottom: "1px solid rgba(255,255,255,0.08)" }}>
        <button onClick={goHome}
          className="text-white font-bold text-sm" style={display}>
          قيّم <span style={{ color: C.amber }}>دكتورك</span>
        </button>
        <span className="flex items-center gap-2">
          <button onClick={openAdmin}
            className="text-xs px-2.5 py-1 rounded-full"
            style={{ background: "rgba(255,255,255,0.1)", color: "#CFE8D8" }}>
            الإدارة
          </button>
          {auth === "unknown" ? (
            <span className="text-xs px-2.5 py-1 rounded-full" style={{ visibility: "hidden" }}>دخول</span>
          ) : auth === "authenticated" ? (
            <span className="flex items-center gap-2">
              <span className="text-xs px-2.5 py-1 rounded-full"
                style={{ background: "rgba(255,255,255,0.1)", color: "#CFE8D8" }}>
                موثّق
              </span>
              <button onClick={logout} className="text-xs" style={{ color: "#CFE8D8" }}>خروج</button>
            </span>
          ) : (
            <button onClick={() => setShowLogin(true)}
              className="text-xs px-2.5 py-1 rounded-full"
              style={{ background: "rgba(255,255,255,0.1)", color: "#CFE8D8" }}>
              دخول
            </button>
          )}
        </span>
      </header>

      {view.page === "home" && <Home onOpen={(id) => setView({ page: "prof", profId: id })} />}
      {view.page === "prof" && (
        <Profile
          professorId={view.profId}
          onBack={() => setView({ page: "home", profId: null })}
          signedIn={auth === "authenticated"}
          authReady={auth !== "unknown"}
          onRequireAuth={() => setShowLogin(true)}
        />
      )}
      {view.page === "admin" && <Admin onBack={goHome} />}
      {showLogin && (
        <LoginPanel
          onClose={() => setShowLogin(false)}
          onSuccess={() => { setAuth("authenticated"); setShowLogin(false); }}
        />
      )}
    </div>
  );
}
