import { useContext } from "react";
import { AuthContext } from "../auth_context";


export function useAuth() {
  const context = useContext(AuthContext);

  if (context === undefined) {
    throw new Error(
      "useAuth mora biti korišćen unutar AuthProvider komponente.",
    );
  }

  return context;
}
