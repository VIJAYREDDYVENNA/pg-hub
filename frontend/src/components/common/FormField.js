/** Bootstrap form group bound to react-hook-form `register`, with server/client error display. */
export default function FormField({ label, name, register, rules, errors, type = 'text', as, options, help, className = '', ...rest }) {
  const error = errors?.[name]?.message;
  const id = `f-${name}`;
  const common = { id, className: `${as === 'select' ? 'form-select' : 'form-control'} ${error ? 'is-invalid' : ''}`, ...register(name, rules), ...rest };
  return (
    <div className={`mb-3 ${className}`}>
      {label && <label htmlFor={id} className="form-label">{label}{rules?.required && <span className="text-danger ms-1" aria-hidden="true">*</span>}</label>}
      {as === 'select' ? (
        <select {...common}>
          {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      ) : as === 'textarea' ? (
        <textarea rows={3} {...common} />
      ) : (
        <input type={type} {...common} />
      )}
      {help && !error && <div className="form-text">{help}</div>}
      {error && <div className="invalid-feedback d-block">{error}</div>}
    </div>
  );
}

/** Copies server-side validation errors into react-hook-form. Returns true when any were applied. */
export function applyServerErrors(err, setError) {
  const fe = err?.response?.data?.fieldErrors || {};
  Object.entries(fe).forEach(([k, msg]) => setError(k, { type: 'server', message: msg }));
  return Object.keys(fe).length > 0;
}
