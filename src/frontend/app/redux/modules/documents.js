import request from 'superagent';
import { createSelector } from 'reselect';

import { normalizeDocument, normalizeDocuments } from 'utils/normalize';
import * as Consts from './common';

// Constants

const FETCH_DOCUMENTS_FINISHED = 'documents/FETCH_DOCUMENTS_FINISHED';
const FETCH_DOCUMENTS_REQUEST = 'documents/FETCH_DOCUMENTS_REQUEST';
const FETCH_DOCUMENT_REQUEST = 'documents/FETCH_DOCUMENT_REQUEST';
const LOAD_DOCUMENT = 'documents/LOAD_DOCUMENT';

// Reducer

const initialState = {
  // Metadata
  $loading: false,

  // Mapping of document ID --> document object
  documents: {},

  // IDs of documents in the current page
  current: [],

  // Pagination information
  pageNumber: 1,
  maxPages: 1,
};

export default function reducer(state = initialState, action = {}) {
  switch (action.type) {
  case FETCH_DOCUMENTS_REQUEST:
    return { ...state, $loading: true };

  case FETCH_DOCUMENTS_FINISHED:
    return { ...state, $loading: false };

  case FETCH_DOCUMENT_REQUEST:
    return {
      ...state,
      documents: {
        ...state.documents,
        [action.id]: {
          ...state.documents[action.id],
          $loading: true,
        },
      },
    };

  case LOAD_DOCUMENT:
    return {
      ...state,
      documents: {
        ...state.documents,
        [action.document.id]: action.document,
      },
    };

  case Consts.UPDATE_DOCUMENTS:
    const documents = Object.assign({}, state.documents, action.documents);
    return {
      ...state,
      documents,
      current: action.current,
      pageNumber: action.pageNumber || 1,
      maxPages: action.maxPages || 1,
    };

  default:
    return state;
  }
}

// Action Creators

export function fetchDocuments(page = 1) {
  return (dispatch) => {
    dispatch({ type: FETCH_DOCUMENTS_REQUEST });

    request
    .get('/api/documents')
    .query({ page })
    .end(function (err, res) {
      dispatch({ type: FETCH_DOCUMENTS_FINISHED });

      if (err) {
        dispatch({ type: Consts.REQUEST_ERROR, error: err });
        return;
      }

      // Pagination information
      const totalItems = +(res.headers['x-pagination-total'] || 0);
      const perPage = +(res.headers['x-pagination-limit'] || 20);

      // Normalize the response and only pull out the document bodies.
      const norm = normalizeDocuments(res.body);
      dispatch({
        type: Consts.UPDATE_DOCUMENTS,
        documents: norm.entities.documents || {},
        current: norm.result,

        pageNumber: page,
        maxPages: Math.ceil(totalItems / perPage),
      });
    });
  };
}

export function fetchDocument(id) {
  return (dispatch, getState) => {
    // Do nothing if the document is already loading.
    const { documents } = getState();
    if (documents.documents[id] && documents.documents[id].$loading) {
      return;
    }

    dispatch({ type: FETCH_DOCUMENT_REQUEST, id });

    request
    .get('/api/documents/' + id)
    .end(function (err, res) {
      if (err) {
        dispatch({ type: Consts.REQUEST_ERROR, error: err });
        return;
      }

      // Normalize the response and only pull out the document.
      const norm = normalizeDocument(res.body);
      const doc = norm.entities.documents[id];

      dispatch({
        type: LOAD_DOCUMENT,
        document: doc || {},
      });

      // TODO: update tags
    });
  };
}

// Selectors

export const currentDocumentIDsSelector = (state) => state.documents.current;
export const allDocumentsSelector = (state) => state.documents.documents;

export const currentDocumentsSelector = createSelector(
  currentDocumentIDsSelector,
  allDocumentsSelector,
  (ids, docs) => ids.map(i => docs[i])
);
