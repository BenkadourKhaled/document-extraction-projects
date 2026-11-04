/**
 * Singleton MSW server instance exported for use in test suites that need
 * fine-grained control over handlers (e.g. overriding for error scenarios).
 */
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

export const server = setupServer(...handlers);

