import { useState } from "react";
import { decodeJwt, getToken, setToken } from "./api/client";
import { AuthPanel } from "./components/AuthPanel";
import { BackendPanel } from "./components/BackendPanel";
import { CreatePromptForm } from "./components/CreatePromptForm";
import { PromptDetail } from "./components/PromptDetail";
import { PromptList } from "./components/PromptList";
import { RequestLog } from "./components/RequestLog";
import { SessionPanel } from "./components/SessionPanel";

export function App() {
  const [token, setTokenState] = useState<string | null>(getToken());
  const [selectedId, setSelectedId] = useState<string | null>(null);
  // Zaehler statt Callback-Kette: hochzaehlen laedt Liste und Tags neu.
  const [reloadKey, setReloadKey] = useState(0);

  const claims = token === null ? null : decodeJwt(token);
  const email = typeof claims?.["email"] === "string" ? (claims["email"] as string) : "";

  function acceptToken(newToken: string) {
    setToken(newToken);
    setTokenState(newToken);
    setSelectedId(null);
    setReloadKey((k) => k + 1);
  }

  function logout() {
    setToken(null);
    setTokenState(null);
    setSelectedId(null);
  }

  return (
    <>
      <header className="app">
        <h1>PromptLib</h1>
        <span className="sub">
          Minimal-Oberflaeche fuer die REST-API. Jedes Bedienelement gehoert zu genau einem
          Endpoint; die Kaesten erklaeren, was im Backend dahinter passiert.
        </span>
      </header>

      <div className="layout">
        <main>
          {token === null || claims === null ? (
            <AuthPanel onToken={acceptToken} />
          ) : (
            <>
              <SessionPanel token={token} email={email} onLogout={logout} />
              {selectedId === null ? (
                <>
                  <PromptList
                    ownUserId={claims.sub}
                    reloadKey={reloadKey}
                    onSelect={setSelectedId}
                  />
                  <CreatePromptForm
                    onCreated={(prompt) => {
                      setReloadKey((k) => k + 1);
                      setSelectedId(prompt.id);
                    }}
                  />
                </>
              ) : (
                <PromptDetail
                  promptId={selectedId}
                  ownUserId={claims.sub}
                  onBack={() => setSelectedId(null)}
                  onChanged={() => setReloadKey((k) => k + 1)}
                />
              )}
            </>
          )}
        </main>

        <aside>
          <BackendPanel />
          <RequestLog />
        </aside>
      </div>
    </>
  );
}
