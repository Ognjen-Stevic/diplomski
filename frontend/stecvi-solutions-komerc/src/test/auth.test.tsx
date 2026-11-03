import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { AuthProvider } from "../features/auth/auth_provider";
import { authApi, AuthApiError } from "../features/auth/auth_api";
import { useAuth } from "../features/auth/hooks/useAuth";
const key = "stecvi_access_token";
const user = {
  id: 1,
  name: "First User",
  email: "first@example.test",
  role: "CEO" as const,
  permissions: [],
};
function Probe() {
  const auth = useAuth();
  return (
    <>
      <output>{auth.user?.name ?? "Guest"}</output>
      <p>{auth.restorationError}</p>
      <button onClick={auth.retrySession}>Retry</button>
      <button onClick={auth.logout}>Logout</button>
    </>
  );
}
beforeEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});
it("temporary restoration failure preserves token and retries without looping", async () => {
  localStorage.setItem(key, "one");
  const me = vi
    .spyOn(authApi, "me")
    .mockRejectedValueOnce(new AuthApiError("server", 503))
    .mockResolvedValueOnce(user);
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  await screen.findByText(/Sesiju/);
  expect(localStorage.getItem(key)).toBe("one");
  expect(me).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByText("Retry"));
  await screen.findByText("First User");
  expect(me).toHaveBeenCalledTimes(2);
});
it("unauthorized restoration invalidates only the matching token", async () => {
  localStorage.setItem(key, "one");
  vi.spyOn(authApi, "me").mockRejectedValue(new AuthApiError("expired", 401));
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  await waitFor(() => expect(localStorage.getItem(key)).toBeNull());
  expect(screen.getByText("Guest")).toBeInTheDocument();
});
it("storage events clear the old user and restore the other account", async () => {
  localStorage.setItem(key, "one");
  vi.spyOn(authApi, "me")
    .mockResolvedValueOnce(user)
    .mockResolvedValueOnce({ ...user, id: 2, name: "Second User" });
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  await screen.findByText("First User");
  act(() => {
    localStorage.removeItem(key);
    window.dispatchEvent(new StorageEvent("storage", { key }));
  });
  expect(screen.getByText("Guest")).toBeInTheDocument();
  act(() => {
    localStorage.setItem(key, "two");
    window.dispatchEvent(new StorageEvent("storage", { key }));
  });
  expect(screen.queryByText("First User")).not.toBeInTheDocument();
  await screen.findByText("Second User");
});
it("a late response from a previous tab session cannot restore the old account", async () => {
  let resolveOld!: (value: typeof user) => void;
  const old = new Promise<typeof user>((resolve) => {
    resolveOld = resolve;
  });
  localStorage.setItem(key, "old");
  vi.spyOn(authApi, "me")
    .mockReturnValueOnce(old)
    .mockResolvedValueOnce({ ...user, name: "New User" });
  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
  act(() => {
    localStorage.setItem(key, "new");
    window.dispatchEvent(new StorageEvent("storage", { key }));
  });
  await screen.findByText("New User");
  await act(async () => resolveOld(user));
  expect(screen.getByText("New User")).toBeInTheDocument();
  expect(screen.queryByText("First User")).not.toBeInTheDocument();
});
