import { telLink, whatsappLink } from '../../utils/format';
import Icon from './Icon';

/** Call / open-in-WhatsApp shortcuts. These open the user's own apps; the system sends nothing. */
export default function ContactButtons({ mobile, text, size = 'sm' }) {
  if (!mobile) return null;
  return (
    <span className="d-inline-flex gap-1">
      <a className={`btn btn-${size} btn-light`} href={telLink(mobile)} title={`Call ${mobile}`} aria-label={`Call ${mobile}`}>
        <Icon className="bi bi-telephone" />
      </a>
      <a className={`btn btn-${size} btn-whatsapp`} href={whatsappLink(mobile, text)} target="_blank" rel="noreferrer"
        title="Open chat in WhatsApp" aria-label={`Open WhatsApp chat with ${mobile}`}>
        <Icon className="bi bi-whatsapp" />
      </a>
    </span>
  );
}
