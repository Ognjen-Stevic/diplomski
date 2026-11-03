import { useCallback, useEffect, useRef, useState } from "react";
import { apiRequest } from "../../app/api/http_client";
import {
  useSessionCurrent,
  useSessionRequest,
} from "../../shared/hooks/use_session_request";
import { SessionScope } from "../../shared/components/session_scope";
import { useAuth } from "../auth/hooks/useAuth";
type Model = {
  version: string;
  metrics: string;
  eligible: boolean;
  active: boolean;
};
export function AiAdminTools() {
  const { user } = useAuth();
  return user?.role === "CEO" ? (
    <SessionScope>
      <Tools />
    </SessionScope>
  ) : null;
}
function Tools() {
  const [jobId, setJobId] = useState("");
  const [error, setError] = useState("");
  const guard = useRef(false);
  const [busy, setBusy] = useState(false);
  const current = useSessionCurrent();
  const load = useCallback(
    (signal: AbortSignal) =>
      apiRequest<{ status: string; message?: string; result?: unknown }>(
        `/api/ai/admin/jobs/${jobId}`,
        { signal },
      ),
    [jobId],
  );
  const job = useSessionRequest(`ai-job:${jobId}`, load, Boolean(jobId));
  const loadModels = useCallback(
    (signal: AbortSignal) =>
      apiRequest<Model[]>("/api/ai/admin/models", { signal }),
    [],
  );
  const models = useSessionRequest("ai-models", loadModels);
  useEffect(() => {
    if (
      !jobId ||
      job.data?.status === "COMPLETED" ||
      job.data?.status === "FAILED"
    )
      return;
    const timer = setInterval(job.reload, 2000);
    return () => clearInterval(timer);
  }, [jobId, job.data?.status, job.reload]);
  const execute = async (path: string) => {
    if (guard.current) return;
    guard.current = true;
    setBusy(true);
    setError("");
    try {
      const result = await apiRequest<{ jobId: string } | undefined>(path, {
        method: "POST",
      });
      if (current()) {
        if (result) setJobId(result.jobId);
        models.reload();
      }
    } catch (e) {
      if (current())
        setError(e instanceof Error ? e.message : "Operacija nije uspela.");
    } finally {
      guard.current = false;
      if (current()) setBusy(false);
    }
  };
  return (
    <section className="my-6 rounded-xl border border-dust-grey p-4">
      <h2 className="font-bold text-yale-blue">Administracija modela — CEO</h2>
      <p className="my-2 text-sm">
        Prikupljanje istorije ne obučava model. Obuka zahteva sazrele vremenske
        prozore i uspešnu validaciju; aktivacija je zasebna odluka.
      </p>
      <button
        disabled={busy || job.data?.status === "RUNNING"}
        onClick={() => void execute("/api/ai/admin/refresh")}
        className="mr-3 underline"
      >
        Prikupi podatke / osveži procene
      </button>
      <button
        disabled={busy || job.data?.status === "RUNNING"}
        onClick={() => void execute("/api/ai/admin/train")}
        className="underline"
      >
        Pokreni obuku
      </button>
      {error && <p role="alert">{error}</p>}
      {job.error && <p role="alert">{job.error}</p>}
      {job.data && (
        <div role="status">
          {job.data.status} {job.data.message}
          <pre className="overflow-auto text-xs">
            {job.data.result ? JSON.stringify(job.data.result, null, 2) : ""}
          </pre>
        </div>
      )}
      <button className="my-2 underline" onClick={models.reload}>
        Osveži listu modela
      </button>
      {models.error && <p role="alert">{models.error}</p>}
      {models.data?.length === 0 && <p>Nema obučenih modela.</p>}
      {models.data?.map((model) => (
        <article
          key={model.version}
          className="my-2 border-t border-dust-grey py-2"
        >
          <p>
            {model.version} ·{" "}
            {model.active
              ? "Aktivan"
              : model.eligible
                ? "Validacija uspešna"
                : "Validacija nije uspešna"}
          </p>
          <details>
            <summary>Metrike evaluacije</summary>
            <pre className="overflow-auto text-xs">{model.metrics}</pre>
          </details>
          {model.eligible && !model.active && (
            <button
              disabled={busy}
              className="underline"
              onClick={() =>
                void execute(`/api/ai/admin/models/${model.version}/activate`)
              }
            >
              Aktiviraj validirani model
            </button>
          )}
        </article>
      ))}
    </section>
  );
}
