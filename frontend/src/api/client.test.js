import { errorMessage } from './client';

describe('errorMessage', () => {
  it('prefers the server message', () => {
    expect(errorMessage({ response: { status: 409, data: { message: 'Slot taken' } } })).toBe('Slot taken');
  });

  it('lists field validation errors', () => {
    const err = { response: { status: 400, data: { message: 'Validation failed', fieldErrors: { email: 'must be a well-formed email address' } } } };
    expect(errorMessage(err)).toContain('email: must be a well-formed email address');
  });

  it('explains network failures and timeouts', () => {
    expect(errorMessage({ code: 'ERR_NETWORK' })).toMatch(/cannot reach the server/i);
    expect(errorMessage({ code: 'ECONNABORTED' })).toMatch(/too long/i);
  });

  it('falls back to a generic message', () => {
    expect(errorMessage({ response: { status: 500, data: {} } }, 'Boom')).toBe('Boom');
  });
});
